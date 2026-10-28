package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.CreditPackageDTO;
import com.clinica.escuta.DTO.CreditTransactionDTO;
import com.clinica.escuta.annotation.Auditable;
import com.clinica.escuta.model.CreditPackage;
import com.clinica.escuta.model.CreditTransaction;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.CreditPackageRepository;
import com.clinica.escuta.repository.CreditTransactionRepository;
import com.clinica.escuta.repository.UnitRepository;
import com.clinica.escuta.repository.UserRepository;
import com.clinica.escuta.service.CreditService;
import com.clinica.escuta.service.TelegramNotificationService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/credits")
public class CreditPackagesController {

    private final CreditService creditService;
    private final CreditPackageRepository creditPackageRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final TelegramNotificationService telegramNotificationService;

    public CreditPackagesController(
            CreditService creditService,
            CreditPackageRepository creditPackageRepository,
            CreditTransactionRepository creditTransactionRepository,
            UserRepository userRepository,
            UnitRepository unitRepository,
            TelegramNotificationService telegramNotificationService
    ) {
        this.creditService = creditService;
        this.creditPackageRepository = creditPackageRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.telegramNotificationService = telegramNotificationService;
    }

    @Auditable(action = "SOLICITACAO_PACOTE_CREDITO", targetType = "PACOTE_CREDITO")
    @PostMapping("/packages")
    public ResponseEntity<?> createPackageRequest(@RequestBody CreditPackageDTO request) {
        Optional<User> currentUserOpt = getAuthenticatedUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Usuário não autenticado.");
        }
        if (isCallerAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Apenas locatários podem solicitar a compra de pacotes de créditos.");
        }

        if (request == null || request.getUnitId() == null) {
            return ResponseEntity.badRequest().body("Selecione a unidade para o pacote de créditos.");
        }

        Optional<Unit> unitOpt = unitRepository.findById(request.getUnitId());
        if (unitOpt.isEmpty() || !Boolean.TRUE.equals(unitOpt.get().getStatus())) {
            return ResponseEntity.badRequest().body("Unidade inválida ou inativa.");
        }

        String receipt = request.getReceiptImage();
        if (receipt == null || receipt.trim().isEmpty() || "empty".equalsIgnoreCase(receipt.trim())) {
            return ResponseEntity.badRequest().body("O comprovante de pagamento é obrigatório.");
        }

        User author = currentUserOpt.get();
        CreditPackage saved = creditService.createPurchaseRequest(author.getId(), request.getUnitId(), receipt);
        CreditPackageDTO dto = new CreditPackageDTO(saved, author, unitOpt.get(), false);

        if (telegramNotificationService != null) {
            telegramNotificationService.notifyNewCreditPackage(dto);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/packages/my")
    public ResponseEntity<?> listMyPackages(
            @RequestParam(required = false, defaultValue = "false") boolean includeReceipt
    ) {
        Optional<User> currentUserOpt = getAuthenticatedUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Usuário não autenticado.");
        }
        User user = currentUserOpt.get();
        creditService.expireUserOverduePackages(user.getId());

        Map<Integer, Unit> unitMap = loadUnitMap();
        List<CreditPackage> list = creditPackageRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<CreditPackageDTO> dtos = new ArrayList<>(list.size());
        for (CreditPackage p : list) {
            dtos.add(new CreditPackageDTO(p, user, unitMap.get(p.getUnitId()), includeReceipt));
        }
        return ResponseEntity.ok(dtos);
    }

    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("/packages/stats")
    public ResponseEntity<Map<String, Long>> getPackageStats() {
        creditService.expireOverduePackages();
        long pending = creditPackageRepository.countByStatus(CreditService.STATUS_PENDING);
        long approved = creditPackageRepository.countByStatus(CreditService.STATUS_APPROVED);
        long rejected = creditPackageRepository.countByStatus(CreditService.STATUS_REJECTED);
        long expired = creditPackageRepository.countByStatus(CreditService.STATUS_EXPIRED);
        long total = pending + approved + rejected + expired;
        return ResponseEntity.ok(Map.of(
                "pending", pending,
                "approved", approved,
                "rejected", rejected,
                "expired", expired,
                "total", total
        ));
    }

    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("/packages")
    public ResponseEntity<List<CreditPackageDTO>> listAllPackages(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer unitId,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false, defaultValue = "false") boolean includeReceipt
    ) {
        creditService.expireOverduePackages();

        Map<Integer, User> userMap = loadUserMap();
        Map<Integer, Unit> unitMap = loadUnitMap();

        List<CreditPackage> all = creditPackageRepository.findAllByOrderByCreatedAtDesc();
        List<CreditPackageDTO> result = new ArrayList<>();
        for (CreditPackage p : all) {
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                if (!status.equalsIgnoreCase(p.getStatus())) continue;
            }
            if (unitId != null && !Objects.equals(p.getUnitId(), unitId)) {
                continue;
            }
            if (userId != null && !Objects.equals(p.getUserId(), userId)) {
                continue;
            }
            result.add(new CreditPackageDTO(
                    p,
                    userMap.get(p.getUserId()),
                    unitMap.get(p.getUnitId()),
                    includeReceipt
            ));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/packages/{id}/receipt")
    public ResponseEntity<?> getPackageReceipt(@PathVariable Integer id) {
        Optional<User> currentUserOpt = getAuthenticatedUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Usuário não autenticado.");
        }

        Optional<CreditPackage> pkgOpt = creditPackageRepository.findById(id);
        if (pkgOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CreditPackage pkg = pkgOpt.get();
        boolean isAdmin = isCallerAdmin();
        if (!isAdmin && !Objects.equals(pkg.getUserId(), currentUserOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acesso negado a este comprovante.");
        }

        return ResponseEntity.ok(Map.of(
                "id", String.valueOf(pkg.getId()),
                "receiptImage", pkg.getReceiptImage() != null ? pkg.getReceiptImage() : "empty"
        ));
    }

    @Auditable(action = "APROVACAO_PACOTE_CREDITO", targetType = "PACOTE_CREDITO")
    @PreAuthorize("hasAuthority('admin')")
    @PutMapping("/packages/{id}/approve")
    public ResponseEntity<?> approvePackage(@PathVariable Integer id) {
        Optional<User> adminOpt = getAuthenticatedUser();
        try {
            CreditPackage saved = creditService.approvePackage(id, adminOpt.orElse(null));
            User owner = userRepository.findById(saved.getUserId()).orElse(null);
            Unit unit = unitRepository.findById(saved.getUnitId()).orElse(null);
            return ResponseEntity.ok(new CreditPackageDTO(saved, owner, unit, false));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Auditable(action = "REJEICAO_PACOTE_CREDITO", targetType = "PACOTE_CREDITO")
    @PreAuthorize("hasAuthority('admin')")
    @PutMapping("/packages/{id}/reject")
    public ResponseEntity<?> rejectPackage(
            @PathVariable Integer id,
            @RequestBody(required = false) CreditPackageDTO request
    ) {
        Optional<User> adminOpt = getAuthenticatedUser();
        String reason = request != null ? request.getRejectionReason() : null;
        if (reason != null && reason.trim().length() > 300) {
            return ResponseEntity.badRequest().body("O motivo da rejeição deve ter no máximo 300 caracteres.");
        }

        try {
            CreditPackage saved = creditService.rejectPackage(id, reason, adminOpt.orElse(null));
            User owner = userRepository.findById(saved.getUserId()).orElse(null);
            Unit unit = unitRepository.findById(saved.getUnitId()).orElse(null);
            return ResponseEntity.ok(new CreditPackageDTO(saved, owner, unit, false));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/transactions/my")
    public ResponseEntity<?> listMyTransactions(
            @RequestParam(required = false) Integer unitId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        Optional<User> currentUserOpt = getAuthenticatedUser();
        if (currentUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Usuário não autenticado.");
        }
        User user = currentUserOpt.get();
        creditService.expireUserOverduePackages(user.getId());

        Map<Integer, Unit> unitMap = loadUnitMap();
        List<CreditTransaction> all = creditTransactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId());
        List<CreditTransactionDTO> result = new ArrayList<>();
        for (CreditTransaction tx : all) {
            if (unitId != null && !Objects.equals(tx.getUnitId(), unitId)) continue;
            if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type) && !type.equalsIgnoreCase(tx.getType())) continue;
            if (startDate != null && tx.getCreatedAt() != null && tx.getCreatedAt().toLocalDate().isBefore(startDate)) continue;
            if (endDate != null && tx.getCreatedAt() != null && tx.getCreatedAt().toLocalDate().isAfter(endDate)) continue;
            result.add(new CreditTransactionDTO(tx, user, unitMap.get(tx.getUnitId())));
        }
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("/transactions")
    public ResponseEntity<Page<CreditTransactionDTO>> listAllTransactionsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer unitId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        creditService.expireOverduePackages();

        int safeSize = Math.min(Math.max(size, 5), 100);
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );

        Specification<CreditTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (unitId != null) {
                predicates.add(cb.equal(root.get("unitId"), unitId));
            }
            if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(23, 59, 59)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<CreditTransaction> txPage = creditTransactionRepository.findAll(spec, pageable);
        Map<Integer, User> userMap = loadUserMap();
        Map<Integer, Unit> unitMap = loadUnitMap();

        Page<CreditTransactionDTO> dtoPage = txPage.map(tx ->
                new CreditTransactionDTO(tx, userMap.get(tx.getUserId()), unitMap.get(tx.getUnitId()))
        );
        return ResponseEntity.ok(dtoPage);
    }

    private Map<Integer, User> loadUserMap() {
        return userRepository.findAll().stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private Map<Integer, Unit> loadUnitMap() {
        return unitRepository.findAll().stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(Unit::getId, u -> u, (a, b) -> a));
    }

    private boolean isCallerAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if ("admin".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role)) {
                return true;
            }
        }
        return false;
    }

    private Optional<User> getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        Optional<User> byUsername = userRepository.findByUsername(auth.getName());
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return userRepository.findByEmail(auth.getName());
    }
}

