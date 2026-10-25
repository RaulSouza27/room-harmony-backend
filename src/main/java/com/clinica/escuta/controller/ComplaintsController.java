package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.ComplaintDTO;
import com.clinica.escuta.annotation.Auditable;
import com.clinica.escuta.config.DatabaseSequenceSynchronizer;
import com.clinica.escuta.model.Complaint;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.ComplaintRepository;
import com.clinica.escuta.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/complaints")
public class ComplaintsController {

    private static final int DAILY_LIMIT = 3;
    private static final int MAX_COMPLAINT_LENGTH = 255;
    private static final int MIN_RESOLUTION_LENGTH = 5;
    private static final int MAX_RESOLUTION_LENGTH = 500;

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final DatabaseSequenceSynchronizer sequenceSynchronizer;

    @Value("${app.key:minha-chave-secreta-super-segura-de-32-chars!}")
    private String appSecret = "minha-chave-secreta-super-segura-de-32-chars!";

    public ComplaintsController(
            ComplaintRepository complaintRepository,
            UserRepository userRepository
    ) {
        this(complaintRepository, userRepository, null);
    }

    @Autowired
    public ComplaintsController(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            DatabaseSequenceSynchronizer sequenceSynchronizer
    ) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.sequenceSynchronizer = sequenceSynchronizer;
    }

    /**
     * Retorna apenas a cota diária do locatário autenticado (sem expor os textos enviados).
     */
    @GetMapping("/quota")
    public ResponseEntity<?> getDailyQuota() {
        Optional<User> currentUser = getAuthenticatedUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Usuário não autenticado.");
        }

        String dailyHash = computeDailyHash(currentUser.get().getId(), LocalDate.now());
        long usedToday = complaintRepository.countBySenderDailyHash(dailyHash);
        long remaining = Math.max(0, DAILY_LIMIT - usedToday);

        return ResponseEntity.ok(Map.of(
                "used", usedToday,
                "limit", DAILY_LIMIT,
                "remaining", remaining
        ));
    }

    /**
     * Lista apenas as manifestações IDENTIFICADAS (user_id != null) do locatário autenticado,
     * permitindo acompanhar o status e visualizar o parecer de resolução da administração.
     */
    @GetMapping("/my")
    public ResponseEntity<?> readMyIdentifiedComplaints() {
        Optional<User> currentUser = getAuthenticatedUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Usuário não autenticado.");
        }

        User author = currentUser.get();
        List<Complaint> list = complaintRepository.findByUserIdOrderByCreatedAtDesc(author.getId());
        List<ComplaintDTO> dtos = new ArrayList<>(list.size());
        for (Complaint c : list) {
            dtos.add(new ComplaintDTO(c, author));
        }
        return ResponseEntity.ok(dtos);
    }

    /**
     * Criação de reclamação/manifestação (exclusivo para locatários, até 3 por dia).
     * Não utiliza @Auditable para preservar 100% o anonimato quando user_id for NULL.
     */
    @PostMapping
    public ResponseEntity<?> createComplaint(@RequestBody ComplaintDTO request) {
        if (isCallerAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Administradores não podem registrar reclamações na Ouvidoria.");
        }

        Optional<User> currentUser = getAuthenticatedUser();
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Usuário autenticado não encontrado.");
        }

        if (request.getComplaints() == null || request.getComplaints().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("O texto da manifestação é obrigatório.");
        }

        String text = request.getComplaints().trim();
        if (text.length() > MAX_COMPLAINT_LENGTH) {
            return ResponseEntity.badRequest()
                    .body("A manifestação deve ter no máximo " + MAX_COMPLAINT_LENGTH + " caracteres.");
        }

        String dailyHash = computeDailyHash(currentUser.get().getId(), LocalDate.now());
        long usedToday = complaintRepository.countBySenderDailyHash(dailyHash);
        if (usedToday >= DAILY_LIMIT) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Limite diário atingido: você já enviou " + DAILY_LIMIT + " manifestações hoje.");
        }

        boolean isAnonymous = Boolean.TRUE.equals(request.getAnonymous());
        Integer authorId = isAnonymous ? null : currentUser.get().getId();

        try {
            Complaint entity = buildComplaintEntity(authorId, text, dailyHash);
            complaintRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            if (sequenceSynchronizer != null) {
                sequenceSynchronizer.syncSequence("complaints");
            }
            Complaint retryEntity = buildComplaintEntity(authorId, text, dailyHash);
            complaintRepository.save(retryEntity);
        }

        long newUsed = usedToday + 1;
        return ResponseEntity.ok(Map.of(
                "success", true,
                "used", newUsed,
                "limit", DAILY_LIMIT,
                "remaining", Math.max(0, DAILY_LIMIT - newUsed)
        ));
    }

    /**
     * Listagem completa de todas as manifestações (exclusivo para administradores).
     */
    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("/readAll")
    public ResponseEntity<List<ComplaintDTO>> readAll() {
        List<Complaint> list = complaintRepository.findAllByOrderByCreatedAtDesc();
        Map<Integer, User> userMap = userRepository.findAll().stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<ComplaintDTO> dtos = new ArrayList<>(list.size());
        for (Complaint c : list) {
            User author = c.getUserId() != null ? userMap.get(c.getUserId()) : null;
            dtos.add(new ComplaintDTO(c, author));
        }
        return ResponseEntity.ok(dtos);
    }

    /**
     * Marca uma reclamação como resolvida (exigindo parecer obrigatório) ou não resolvida (exclusivo para administradores).
     */
    @Auditable(action = "ALTERACAO_STATUS_OUVIDORIA", targetType = "OUVIDORIA")
    @PreAuthorize("hasAuthority('admin')")
    @PutMapping("/{id}/solved")
    public ResponseEntity<?> updateSolvedStatus(
            @PathVariable Integer id,
            @RequestBody(required = false) ComplaintDTO request
    ) {
        Optional<Complaint> opt = complaintRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint complaint = opt.get();
        boolean newSolved = (request != null && request.getSolved() != null)
                ? request.getSolved()
                : !Boolean.TRUE.equals(complaint.getSolved());

        if (newSolved) {
            String notes = (request != null && request.getResolutionNotes() != null)
                    ? request.getResolutionNotes().trim()
                    : "";
            if (notes.length() < MIN_RESOLUTION_LENGTH) {
                return ResponseEntity.badRequest()
                        .body("É obrigatório informar o parecer de resolução (mínimo de " + MIN_RESOLUTION_LENGTH + " caracteres) para marcar como resolvida.");
            }
            if (notes.length() > MAX_RESOLUTION_LENGTH) {
                return ResponseEntity.badRequest()
                        .body("O parecer de resolução deve ter no máximo " + MAX_RESOLUTION_LENGTH + " caracteres.");
            }

            Optional<User> adminUser = getAuthenticatedUser();
            String resolvedBy = adminUser
                    .map(u -> u.getUsername() + (u.getEmail() != null ? " (" + u.getEmail() + ")" : ""))
                    .orElseGet(() -> {
                        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                        return auth != null && auth.getName() != null ? auth.getName() : "Administrador";
                    });

            complaint.setSolved(true);
            complaint.setResolutionNotes(notes);
            complaint.setResolvedBy(resolvedBy);
            complaint.setResolvedAt(LocalDateTime.now());
        } else {
            complaint.setSolved(false);
            complaint.setResolutionNotes(null);
            complaint.setResolvedBy(null);
            complaint.setResolvedAt(null);
        }

        Complaint saved = complaintRepository.save(complaint);
        User author = saved.getUserId() != null
                ? userRepository.findById(saved.getUserId()).orElse(null)
                : null;
        return ResponseEntity.ok(new ComplaintDTO(saved, author));
    }

    private Complaint buildComplaintEntity(Integer authorId, String text, String dailyHash) {
        Complaint entity = new Complaint();
        entity.setId(null);
        entity.setUserId(authorId);
        entity.setComplaints(text);
        entity.setSolved(false);
        entity.setSenderDailyHash(dailyHash);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private boolean isCallerAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("admin"::equals);
    }

    private Optional<User> getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return Optional.empty();
        return userRepository.findByUsername(auth.getName());
    }

    private String computeDailyHash(Integer userId, LocalDate date) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = userId + ":" + date + ":" + appSecret;
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            return userId + "-" + date;
        }
    }
}

