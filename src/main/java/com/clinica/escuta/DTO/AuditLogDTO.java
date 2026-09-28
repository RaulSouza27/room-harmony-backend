package com.clinica.escuta.DTO;

import com.clinica.escuta.model.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDTO {
    private Long id;
    private OffsetDateTime createdAt;
    private Integer userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private String action;
    private String targetType;
    private String targetId;
    private String targetName;
    private String details;
    private String ipAddress;

    public static AuditLogDTO fromEntity(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .createdAt(log.getCreatedAt())
                .userId(log.getUserId())
                .userName(log.getUserName())
                .userEmail(log.getUserEmail())
                .userRole(log.getUserRole())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .targetName(log.getTargetName())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .build();
    }
}
