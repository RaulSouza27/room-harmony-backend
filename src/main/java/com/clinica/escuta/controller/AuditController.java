package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.AuditLogDTO;
import com.clinica.escuta.model.AuditLog;
import com.clinica.escuta.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        Specification<AuditLog> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null && !action.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action.trim()));
            }

            if (targetType != null && !targetType.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("targetType"), targetType.trim()));
            }

            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate userNameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("userName")), searchPattern);
                Predicate userEmailMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("userEmail")), searchPattern);
                Predicate targetNameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("targetName")), searchPattern);
                Predicate detailsMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("details")), searchPattern);
                predicates.add(criteriaBuilder.or(userNameMatch, userEmailMatch, targetNameMatch, detailsMatch));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLog> auditLogsPage = auditLogRepository.findAll(spec, pageable);
        Page<AuditLogDTO> dtoPage = auditLogsPage.map(AuditLogDTO::fromEntity);

        return ResponseEntity.ok(dtoPage);
    }
}
