package com.clinica.escuta.event;

import com.clinica.escuta.model.AuditLog;
import com.clinica.escuta.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    public void handleAuditEvent(AuditEvent event) {
        try {
            AuditLog logEntry = AuditLog.builder()
                    .createdAt(OffsetDateTime.now())
                    .userId(event.getUserId())
                    .userName(event.getUserName() != null ? event.getUserName() : "Sistema")
                    .userEmail(event.getUserEmail() != null ? event.getUserEmail() : "sistema@clinica.com")
                    .userRole(event.getUserRole())
                    .action(event.getAction())
                    .targetType(event.getTargetType())
                    .targetId(event.getTargetId())
                    .targetName(event.getTargetName())
                    .details(event.getDetails())
                    .ipAddress(event.getIpAddress())
                    .build();

            auditLogRepository.save(logEntry);
            log.debug("AuditLog gravado com sucesso para ação {}", event.getAction());
        } catch (Exception e) {
            log.error("Erro ao gravar log de auditoria assíncrono para ação {}: {}", event.getAction(), e.getMessage(), e);
        }
    }
}
