package com.clinica.escuta.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {
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
}
