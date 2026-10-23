package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.AuditLogDTO;
import com.clinica.escuta.event.AuditEvent;
import com.clinica.escuta.model.AuditLog;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.AuditLogRepository;
import com.clinica.escuta.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuditController {

  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  @GetMapping
  public ResponseEntity<Page<AuditLogDTO>>
  getAuditLogs(@RequestParam(defaultValue = "0") int page,
               @RequestParam(defaultValue = "20") int size,
               @RequestParam(required = false) String search,
               @RequestParam(required = false) String action,
               @RequestParam(required = false) String targetType) {
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

    Specification<AuditLog> spec = (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (action != null && !action.trim().isEmpty()) {
        predicates.add(
            criteriaBuilder.equal(root.get("action"), action.trim()));
      }

      if (targetType != null && !targetType.trim().isEmpty()) {
        predicates.add(
            criteriaBuilder.equal(root.get("targetType"), targetType.trim()));
      }

      if (search != null && !search.trim().isEmpty()) {
        String searchPattern = "%" + search.trim().toLowerCase() + "%";
        Predicate userNameMatch = criteriaBuilder.like(
            criteriaBuilder.lower(root.get("userName")), searchPattern);
        Predicate userEmailMatch = criteriaBuilder.like(
            criteriaBuilder.lower(root.get("userEmail")), searchPattern);
        Predicate targetNameMatch = criteriaBuilder.like(
            criteriaBuilder.lower(root.get("targetName")), searchPattern);
        Predicate detailsMatch = criteriaBuilder.like(
            criteriaBuilder.lower(root.get("details")), searchPattern);
        predicates.add(criteriaBuilder.or(userNameMatch, userEmailMatch,
                                          targetNameMatch, detailsMatch));
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    Page<AuditLog> auditLogsPage = auditLogRepository.findAll(spec, pageable);
    Page<AuditLogDTO> dtoPage = auditLogsPage.map(AuditLogDTO::fromEntity);

    return ResponseEntity.ok(dtoPage);
  }

  @PostMapping
  public ResponseEntity<?>
  createAuditLog(@RequestBody Map<String, String> payload,
                 HttpServletRequest request) {
    String action = payload.get("action");
    String targetType = payload.get("targetType");
    if (action == null || action.isBlank() || targetType == null ||
        targetType.isBlank()) {
      return ResponseEntity.badRequest().body(
          "action and targetType are required.");
    }

    Authentication auth =
        SecurityContextHolder.getContext().getAuthentication();
    String username = null;
    String userRole = null;
    if (auth != null && auth.isAuthenticated() &&
        !"anonymousUser".equals(auth.getPrincipal())) {
      username = auth.getName();
      userRole = auth.getAuthorities()
                     .stream()
                     .findFirst()
                     .map(a -> a.getAuthority())
                     .orElse(null);
    }

    Integer userId = null;
    String userName = username != null ? username : "Sistema";
    String userEmail = username != null ? username : "sistema@clinica.com";

    if (username != null) {
      Optional<User> userOpt = userRepository.findByUsername(username);
      if (userOpt.isEmpty()) {
        userOpt = userRepository.findByEmail(username);
      }
      if (userOpt.isPresent()) {
        User u = userOpt.get();
        userId = u.getId();
        userName = u.getUsername();
        userEmail = u.getEmail();
        if (userRole == null) {
          userRole = u.getAccessLevel();
        }
      }
    }

    String xForwardedFor = request.getHeader("X-Forwarded-For");
    String ipAddress = (xForwardedFor != null && !xForwardedFor.isEmpty())
                           ? xForwardedFor.split(",")[0].trim()
                           : request.getRemoteAddr();

    AuditEvent event = AuditEvent.builder()
                           .userId(userId)
                           .userName(userName)
                           .userEmail(userEmail)
                           .userRole(userRole)
                           .action(action.trim())
                           .targetType(targetType.trim())
                           .targetId(payload.get("targetId"))
                           .targetName(payload.get("targetName"))
                           .details(payload.get("details"))
                           .ipAddress(ipAddress)
                           .build();

    eventPublisher.publishEvent(event);
    return ResponseEntity.ok().build();
  }
}
