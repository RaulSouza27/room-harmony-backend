package com.clinica.escuta.service;

import com.clinica.escuta.config.DatabaseSequenceSynchronizer;
import com.clinica.escuta.model.CreditPackage;
import com.clinica.escuta.model.CreditTransaction;
import com.clinica.escuta.model.Reservation;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.CreditPackageRepository;
import com.clinica.escuta.repository.CreditTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CreditService {

    public static final int PACKAGE_CREDITS = 12;
    public static final int PACKAGE_VALIDITY_DAYS = 30;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    public static final String TX_CREDITO_PACOTE = "CREDITO_PACOTE";
    public static final String TX_USO_RESERVA = "USO_RESERVA";
    public static final String TX_ESTORNO_CANCELAMENTO = "ESTORNO_CANCELAMENTO";
    public static final String TX_AJUSTE_EDICAO = "AJUSTE_EDICAO";
    public static final String TX_EXPIRACAO = "EXPIRACAO";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CreditPackageRepository creditPackageRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final DatabaseSequenceSynchronizer sequenceSynchronizer;

    public CreditService(
            CreditPackageRepository creditPackageRepository,
            CreditTransactionRepository creditTransactionRepository
    ) {
        this(creditPackageRepository, creditTransactionRepository, null);
    }

    @Autowired
    public CreditService(
            CreditPackageRepository creditPackageRepository,
            CreditTransactionRepository creditTransactionRepository,
            DatabaseSequenceSynchronizer sequenceSynchronizer
    ) {
        this.creditPackageRepository = creditPackageRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.sequenceSynchronizer = sequenceSynchronizer;
    }

    /**
     * Rotina diária + sob demanda para expirar pacotes cujo prazo de 30 dias já venceu,
     * registrando no extrato os créditos que expiraram sem uso.
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void expireOverduePackages() {
        LocalDateTime now = LocalDateTime.now();
        List<CreditPackage> expiredList = creditPackageRepository.findExpiredApprovedPackages(now);
        processExpirationList(expiredList, now);
    }

    @Transactional
    public void expireUserOverduePackages(Integer userId) {
        if (userId == null) return;
        LocalDateTime now = LocalDateTime.now();
        List<CreditPackage> expiredList = creditPackageRepository.findUserExpiredApprovedPackages(userId, now);
        processExpirationList(expiredList, now);
    }

    private void processExpirationList(List<CreditPackage> expiredList, LocalDateTime now) {
        if (expiredList == null || expiredList.isEmpty()) return;
        for (CreditPackage pkg : expiredList) {
            int leftover = pkg.getRemainingCredits() != null ? pkg.getRemainingCredits() : 0;
            pkg.setStatus(STATUS_EXPIRED);
            pkg.setRemainingCredits(0);
            creditPackageRepository.save(pkg);

            if (leftover > 0) {
                CreditTransaction tx = new CreditTransaction();
                tx.setPackageId(pkg.getId());
                tx.setUserId(pkg.getUserId());
                tx.setUnitId(pkg.getUnitId());
                tx.setReservationId(null);
                tx.setType(TX_EXPIRACAO);
                tx.setAmount(-leftover);
                tx.setBalanceAfter(0);
                tx.setDescription("Expiração de " + leftover + " crédito(s) não utilizado(s) do pacote #" + pkg.getId());
                tx.setPerformedByUserId(null);
                tx.setPerformedByName("Sistema (Validade 30 dias)");
                tx.setCreatedAt(now);
                saveTransactionWithRetry(tx);
            }
        }
    }

    @Transactional
    public CreditPackage createPurchaseRequest(Integer userId, Integer unitId, String receiptImage) {
        CreditPackage pkg = new CreditPackage();
        pkg.setUserId(userId);
        pkg.setUnitId(unitId);
        pkg.setTotalCredits(PACKAGE_CREDITS);
        pkg.setRemainingCredits(0);
        pkg.setReceiptImage(receiptImage.trim());
        pkg.setStatus(STATUS_PENDING);
        pkg.setCreatedAt(LocalDateTime.now());
        return savePackageWithRetry(pkg);
    }

    @Transactional
    public CreditPackage approvePackage(Integer packageId, User reviewer) {
        CreditPackage pkg = creditPackageRepository.findById(packageId)
                .orElseThrow(() -> new NoSuchElementException("Solicitação de pacote não encontrada."));

        if (!STATUS_PENDING.equalsIgnoreCase(pkg.getStatus())) {
            throw new IllegalStateException("Apenas solicitações pendentes podem ser aprovadas.");
        }

        LocalDateTime now = LocalDateTime.now();
        // Validade: 30 dias a partir da aprovação até o final do 30º dia (23:59:59)
        LocalDateTime expiresAt = now.toLocalDate().plusDays(PACKAGE_VALIDITY_DAYS).atTime(23, 59, 59);

        pkg.setStatus(STATUS_APPROVED);
        pkg.setTotalCredits(PACKAGE_CREDITS);
        pkg.setRemainingCredits(PACKAGE_CREDITS);
        pkg.setReviewedAt(now);
        pkg.setReviewedByUserId(reviewer != null ? reviewer.getId() : null);
        pkg.setReviewedByName(reviewer != null ? reviewer.getUsername() : "Admin");
        pkg.setRejectionReason(null);
        pkg.setExpiresAt(expiresAt);

        CreditPackage saved = creditPackageRepository.save(pkg);

        CreditTransaction tx = new CreditTransaction();
        tx.setPackageId(saved.getId());
        tx.setUserId(saved.getUserId());
        tx.setUnitId(saved.getUnitId());
        tx.setReservationId(null);
        tx.setType(TX_CREDITO_PACOTE);
        tx.setAmount(PACKAGE_CREDITS);
        tx.setBalanceAfter(PACKAGE_CREDITS);
        tx.setDescription("Pacote #" + saved.getId() + " aprovado (+12h válidas até " + expiresAt.toLocalDate().format(DATE_FMT) + ")");
        tx.setPerformedByUserId(reviewer != null ? reviewer.getId() : null);
        tx.setPerformedByName(reviewer != null ? reviewer.getUsername() : "Admin");
        tx.setCreatedAt(now);
        saveTransactionWithRetry(tx);

        return saved;
    }

    @Transactional
    public CreditPackage rejectPackage(Integer packageId, String rejectionReason, User reviewer) {
        CreditPackage pkg = creditPackageRepository.findById(packageId)
                .orElseThrow(() -> new NoSuchElementException("Solicitação de pacote não encontrada."));

        if (!STATUS_PENDING.equalsIgnoreCase(pkg.getStatus())) {
            throw new IllegalStateException("Apenas solicitações pendentes podem ser rejeitadas.");
        }

        LocalDateTime now = LocalDateTime.now();
        pkg.setStatus(STATUS_REJECTED);
        pkg.setRemainingCredits(0);
        pkg.setRejectionReason(rejectionReason != null && !rejectionReason.trim().isEmpty() ? rejectionReason.trim() : null);
        pkg.setReviewedAt(now);
        pkg.setReviewedByUserId(reviewer != null ? reviewer.getId() : null);
        pkg.setReviewedByName(reviewer != null ? reviewer.getUsername() : "Admin");

        return creditPackageRepository.save(pkg);
    }

    /**
     * Calcula quantos créditos válidos o locatário possui na unidade para uma determinada data de reserva.
     * Regra: O pacote deve estar APPROVED, não vencido hoje, e sua data de expiração deve cobrir a data da reserva.
     */
    @Transactional
    public int getAvailableCreditsForDate(Integer userId, Integer unitId, LocalDate reservationDate) {
        expireUserOverduePackages(userId);
        LocalDateTime now = LocalDateTime.now();
        List<CreditPackage> packages = creditPackageRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int sum = 0;
        for (CreditPackage p : packages) {
            if (STATUS_APPROVED.equalsIgnoreCase(p.getStatus())
                    && Objects.equals(p.getUnitId(), unitId)
                    && p.getRemainingCredits() != null
                    && p.getRemainingCredits() > 0
                    && p.getExpiresAt() != null
                    && !p.getExpiresAt().isBefore(now)
                    && (reservationDate == null || !p.getExpiresAt().toLocalDate().isBefore(reservationDate))) {
                sum += p.getRemainingCredits();
            }
        }
        return sum;
    }

    /**
     * Consome créditos usando a regra FEFO (First-Expired, First-Out: o pacote que vence antes é consumido primeiro),
     * desde que o pacote cubra a data em que a reserva irá acontecer.
     */
    @Transactional
    public void consumeCreditsForReservation(
            Reservation reservation,
            Integer unitId,
            String roomName,
            int hoursRequired,
            User actor
    ) {
        if (hoursRequired <= 0) {
            throw new IllegalArgumentException("A quantidade de créditos a consumir deve ser maior que zero.");
        }

        expireUserOverduePackages(reservation.getUserId());
        LocalDateTime now = LocalDateTime.now();
        LocalDate resDate = reservation.getData();

        List<CreditPackage> activePackages = creditPackageRepository.findActivePackagesForUpdate(
                reservation.getUserId(),
                unitId
        );

        List<CreditPackage> eligiblePackages = new ArrayList<>();
        int totalEligibleCredits = 0;
        for (CreditPackage pkg : activePackages) {
            if (pkg.getExpiresAt() != null
                    && !pkg.getExpiresAt().isBefore(now)
                    && !pkg.getExpiresAt().toLocalDate().isBefore(resDate)) {
                eligiblePackages.add(pkg);
                totalEligibleCredits += pkg.getRemainingCredits();
            }
        }

        if (totalEligibleCredits < hoursRequired) {
            throw new IllegalArgumentException(
                    String.format(
                            "Saldo de créditos insuficiente na unidade para a data %s (necessário: %dh, saldo válido na data: %dh). Verifique se seus créditos não vencem antes da data escolhida.",
                            resDate.format(DATE_FMT),
                            hoursRequired,
                            totalEligibleCredits
                    )
            );
        }

        int remainingToDeduct = hoursRequired;
        String timeRange = formatTime(reservation.getHoraInicio()) + "–" + formatTime(reservation.getHoraFim());

        for (CreditPackage pkg : eligiblePackages) {
            if (remainingToDeduct <= 0) break;
            int availableInPkg = pkg.getRemainingCredits();
            int deduct = Math.min(availableInPkg, remainingToDeduct);

            pkg.setRemainingCredits(availableInPkg - deduct);
            creditPackageRepository.save(pkg);

            CreditTransaction tx = new CreditTransaction();
            tx.setPackageId(pkg.getId());
            tx.setUserId(reservation.getUserId());
            tx.setUnitId(unitId);
            tx.setReservationId(reservation.getId());
            tx.setType(TX_USO_RESERVA);
            tx.setAmount(-deduct);
            tx.setBalanceAfter(pkg.getRemainingCredits());
            tx.setDescription(String.format(
                    "Uso em reserva #%d — %s (%s %s)",
                    reservation.getId(),
                    roomName != null ? roomName : ("Sala #" + reservation.getRoomsId()),
                    resDate.format(DATE_FMT),
                    timeRange
            ));
            tx.setPerformedByUserId(actor != null ? actor.getId() : reservation.getUserId());
            tx.setPerformedByName(actor != null ? actor.getUsername() : "Locatário");
            tx.setCreatedAt(now);
            saveTransactionWithRetry(tx);

            remainingToDeduct -= deduct;
        }
    }

    /**
     * Valida se os pacotes que financiaram originalmente uma reserva (ou pacotes complementares)
     * permitem reagendar para a nova data e ajusta eventual diferença de horas.
     */
    @Transactional
    public void validateAndAdjustReservationEdit(
            Reservation reservation,
            Integer unitId,
            String roomName,
            LocalDate newDate,
            LocalTime newStart,
            LocalTime newEnd,
            int newHoursRequired,
            User actor
    ) {
        List<CreditTransaction> txs = creditTransactionRepository.findByReservationIdOrderByIdAsc(reservation.getId());
        Map<Integer, Integer> netByPackage = new LinkedHashMap<>();
        for (CreditTransaction tx : txs) {
            netByPackage.merge(tx.getPackageId(), tx.getAmount(), Integer::sum);
        }

        List<Integer> packageIds = new ArrayList<>(netByPackage.keySet());
        Map<Integer, CreditPackage> pkgMap = new HashMap<>();
        if (!packageIds.isEmpty()) {
            for (CreditPackage p : creditPackageRepository.findAllByIdsForUpdate(packageIds)) {
                pkgMap.put(p.getId(), p);
            }
        }

        // Verifica se todos os pacotes que atualmente financiam esta reserva cobrem a nova data
        for (Map.Entry<Integer, Integer> entry : netByPackage.entrySet()) {
            int netAmount = entry.getValue(); // negativo quando há créditos alocados nesta reserva
            if (netAmount < 0) {
                CreditPackage pkg = pkgMap.get(entry.getKey());
                if (pkg == null) continue;
                if (!Objects.equals(pkg.getUnitId(), unitId)) {
                    throw new IllegalArgumentException("Reservas feitas com crédito só podem ser alteradas para salas da mesma unidade do pacote.");
                }
                if (pkg.getExpiresAt() != null && pkg.getExpiresAt().toLocalDate().isBefore(newDate)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "A nova data (%s) ultrapassa o vencimento do pacote #%d (%s).",
                                    newDate.format(DATE_FMT),
                                    pkg.getId(),
                                    pkg.getExpiresAt().toLocalDate().format(DATE_FMT)
                            )
                    );
                }
            }
        }

        int currentHoursUsed = reservation.getCreditHoursUsed() != null ? reservation.getCreditHoursUsed() : 0;
        int diff = newHoursRequired - currentHoursUsed;

        if (diff > 0) {
            // Precisa debitar +diff créditos adicionais
            Reservation tempRes = new Reservation();
            tempRes.setId(reservation.getId());
            tempRes.setUserId(reservation.getUserId());
            tempRes.setRoomsId(reservation.getRoomsId());
            tempRes.setData(newDate);
            tempRes.setHoraInicio(newStart);
            tempRes.setHoraFim(newEnd);
            consumeAdditionalCreditsForEdit(tempRes, unitId, roomName, diff, actor);
        } else if (diff < 0) {
            // Devolve (-diff) créditos para os pacotes de origem
            int toReturn = -diff;
            LocalDateTime now = LocalDateTime.now();
            List<Integer> reversedPkgIds = new ArrayList<>(netByPackage.keySet());
            Collections.reverse(reversedPkgIds);
            for (Integer pkgId : reversedPkgIds) {
                if (toReturn <= 0) break;
                int net = netByPackage.getOrDefault(pkgId, 0);
                int allocated = Math.max(0, -net);
                if (allocated <= 0) continue;

                CreditPackage pkg = pkgMap.get(pkgId);
                if (pkg == null) continue;

                int refundNow = Math.min(allocated, toReturn);
                pkg.setRemainingCredits(Math.min(pkg.getTotalCredits(), pkg.getRemainingCredits() + refundNow));
                creditPackageRepository.save(pkg);

                CreditTransaction tx = new CreditTransaction();
                tx.setPackageId(pkg.getId());
                tx.setUserId(reservation.getUserId());
                tx.setUnitId(pkg.getUnitId());
                tx.setReservationId(reservation.getId());
                tx.setType(TX_AJUSTE_EDICAO);
                tx.setAmount(refundNow);
                tx.setBalanceAfter(pkg.getRemainingCredits());
                tx.setDescription(String.format(
                        "Estorno parcial por redução de duração na reserva #%d (+%dh)",
                        reservation.getId(),
                        refundNow
                ));
                tx.setPerformedByUserId(actor != null ? actor.getId() : reservation.getUserId());
                tx.setPerformedByName(actor != null ? actor.getUsername() : "Locatário");
                tx.setCreatedAt(now);
                saveTransactionWithRetry(tx);

                toReturn -= refundNow;
            }
        }
    }

    private void consumeAdditionalCreditsForEdit(
            Reservation reservation,
            Integer unitId,
            String roomName,
            int additionalHours,
            User actor
    ) {
        expireUserOverduePackages(reservation.getUserId());
        LocalDateTime now = LocalDateTime.now();
        LocalDate resDate = reservation.getData();

        List<CreditPackage> activePackages = creditPackageRepository.findActivePackagesForUpdate(
                reservation.getUserId(),
                unitId
        );

        List<CreditPackage> eligiblePackages = new ArrayList<>();
        int totalEligibleCredits = 0;
        for (CreditPackage pkg : activePackages) {
            if (pkg.getExpiresAt() != null
                    && !pkg.getExpiresAt().isBefore(now)
                    && !pkg.getExpiresAt().toLocalDate().isBefore(resDate)) {
                eligiblePackages.add(pkg);
                totalEligibleCredits += pkg.getRemainingCredits();
            }
        }

        if (totalEligibleCredits < additionalHours) {
            throw new IllegalArgumentException(
                    String.format(
                            "Saldo insuficiente para aumentar a reserva em +%dh na data %s (disponível: %dh).",
                            additionalHours,
                            resDate.format(DATE_FMT),
                            totalEligibleCredits
                    )
            );
        }

        int remaining = additionalHours;
        for (CreditPackage pkg : eligiblePackages) {
            if (remaining <= 0) break;
            int available = pkg.getRemainingCredits();
            int deduct = Math.min(available, remaining);
            pkg.setRemainingCredits(available - deduct);
            creditPackageRepository.save(pkg);

            CreditTransaction tx = new CreditTransaction();
            tx.setPackageId(pkg.getId());
            tx.setUserId(reservation.getUserId());
            tx.setUnitId(unitId);
            tx.setReservationId(reservation.getId());
            tx.setType(TX_AJUSTE_EDICAO);
            tx.setAmount(-deduct);
            tx.setBalanceAfter(pkg.getRemainingCredits());
            tx.setDescription(String.format(
                    "Complemento por ampliação de duração na reserva #%d (-%dh — %s)",
                    reservation.getId(),
                    deduct,
                    roomName != null ? roomName : ("Sala #" + reservation.getRoomsId())
            ));
            tx.setPerformedByUserId(actor != null ? actor.getId() : reservation.getUserId());
            tx.setPerformedByName(actor != null ? actor.getUsername() : "Locatário");
            tx.setCreatedAt(now);
            saveTransactionWithRetry(tx);

            remaining -= deduct;
        }
    }

    /**
     * Estorna integralmente os créditos consumidos por uma reserva de volta para cada pacote de origem.
     */
    @Transactional
    public void refundCreditsForReservation(Reservation reservation, User actor, String customReason) {
        if (reservation == null || !Boolean.TRUE.equals(reservation.getPaidWithCredits())) {
            return;
        }

        List<CreditTransaction> txs = creditTransactionRepository.findByReservationIdOrderByIdAsc(reservation.getId());
        if (txs.isEmpty()) {
            return;
        }

        // Soma líquida por pacote para esta reserva (negativo = ainda consumido)
        Map<Integer, Integer> netByPackage = new LinkedHashMap<>();
        for (CreditTransaction tx : txs) {
            netByPackage.merge(tx.getPackageId(), tx.getAmount(), Integer::sum);
        }

        List<Integer> packageIds = new ArrayList<>(netByPackage.keySet());
        if (packageIds.isEmpty()) return;

        Map<Integer, CreditPackage> pkgMap = new HashMap<>();
        for (CreditPackage p : creditPackageRepository.findAllByIdsForUpdate(packageIds)) {
            pkgMap.put(p.getId(), p);
        }

        LocalDateTime now = LocalDateTime.now();
        String dateStr = reservation.getData() != null ? reservation.getData().format(DATE_FMT) : "-";
        String timeRange = formatTime(reservation.getHoraInicio()) + "–" + formatTime(reservation.getHoraFim());

        for (Map.Entry<Integer, Integer> entry : netByPackage.entrySet()) {
            int net = entry.getValue();
            if (net >= 0) continue; // Já estornado

            int refundAmount = -net;
            CreditPackage pkg = pkgMap.get(entry.getKey());
            if (pkg == null) continue;

            boolean isStillValid = pkg.getExpiresAt() == null || !pkg.getExpiresAt().isBefore(now);
            if (isStillValid) {
                int newBalance = Math.min(pkg.getTotalCredits(), pkg.getRemainingCredits() + refundAmount);
                pkg.setRemainingCredits(newBalance);
                if (STATUS_EXPIRED.equalsIgnoreCase(pkg.getStatus())) {
                    pkg.setStatus(STATUS_APPROVED);
                }
                creditPackageRepository.save(pkg);
            }

            CreditTransaction refundTx = new CreditTransaction();
            refundTx.setPackageId(pkg.getId());
            refundTx.setUserId(reservation.getUserId());
            refundTx.setUnitId(pkg.getUnitId());
            refundTx.setReservationId(reservation.getId());
            refundTx.setType(TX_ESTORNO_CANCELAMENTO);
            refundTx.setAmount(refundAmount);
            refundTx.setBalanceAfter(pkg.getRemainingCredits());
            String baseDesc = customReason != null && !customReason.isBlank()
                    ? customReason
                    : String.format("Estorno por cancelamento da reserva #%d (%s %s)", reservation.getId(), dateStr, timeRange);
            refundTx.setDescription(baseDesc);
            refundTx.setPerformedByUserId(actor != null ? actor.getId() : null);
            refundTx.setPerformedByName(actor != null ? actor.getUsername() : "Sistema");
            refundTx.setCreatedAt(now);
            saveTransactionWithRetry(refundTx);
        }
    }

    private String formatTime(LocalTime t) {
        if (t == null) return "";
        String s = t.toString();
        return s.length() >= 5 ? s.substring(0, 5) : s;
    }

    private CreditPackage savePackageWithRetry(CreditPackage pkg) {
        try {
            return creditPackageRepository.save(pkg);
        } catch (DataIntegrityViolationException ex) {
            if (sequenceSynchronizer != null) {
                sequenceSynchronizer.syncSequence("credit_packages");
                CreditPackage retry = new CreditPackage();
                retry.setUserId(pkg.getUserId());
                retry.setUnitId(pkg.getUnitId());
                retry.setTotalCredits(pkg.getTotalCredits());
                retry.setRemainingCredits(pkg.getRemainingCredits());
                retry.setReceiptImage(pkg.getReceiptImage());
                retry.setStatus(pkg.getStatus());
                retry.setRejectionReason(pkg.getRejectionReason());
                retry.setCreatedAt(pkg.getCreatedAt());
                return creditPackageRepository.save(retry);
            }
            throw ex;
        }
    }

    private CreditTransaction saveTransactionWithRetry(CreditTransaction tx) {
        try {
            return creditTransactionRepository.save(tx);
        } catch (DataIntegrityViolationException ex) {
            if (sequenceSynchronizer != null) {
                sequenceSynchronizer.syncSequence("credit_transactions");
                CreditTransaction retry = new CreditTransaction();
                retry.setPackageId(tx.getPackageId());
                retry.setUserId(tx.getUserId());
                retry.setUnitId(tx.getUnitId());
                retry.setReservationId(tx.getReservationId());
                retry.setType(tx.getType());
                retry.setAmount(tx.getAmount());
                retry.setBalanceAfter(tx.getBalanceAfter());
                retry.setDescription(tx.getDescription());
                retry.setPerformedByUserId(tx.getPerformedByUserId());
                retry.setPerformedByName(tx.getPerformedByName());
                retry.setCreatedAt(tx.getCreatedAt());
                return creditTransactionRepository.save(retry);
            }
            throw ex;
        }
    }
}

