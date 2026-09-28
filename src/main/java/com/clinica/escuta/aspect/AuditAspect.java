package com.clinica.escuta.aspect;

import com.clinica.escuta.annotation.Auditable;
import com.clinica.escuta.event.AuditEvent;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String username = null;
            String userRole = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                username = auth.getName();
                userRole = auth.getAuthorities().stream()
                        .findFirst()
                        .map(a -> a.getAuthority())
                        .orElse(null);
            }

            Integer userId = null;
            String userEmail = username;
            String userName = username;

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

            String ipAddress = getClientIpAddress();
            Object[] args = joinPoint.getArgs();
            String targetId = extractTargetId(args, result);
            String targetName = extractTargetName(args, result, auditable.targetType());

            AuditEvent auditEvent = AuditEvent.builder()
                    .userId(userId)
                    .userName(userName != null ? userName : "Desconhecido")
                    .userEmail(userEmail != null ? userEmail : "desconhecido@clinica.com")
                    .userRole(userRole)
                    .action(auditable.action())
                    .targetType(auditable.targetType())
                    .targetId(targetId)
                    .targetName(targetName)
                    .details(auditable.details().isEmpty() ? null : auditable.details())
                    .ipAddress(ipAddress)
                    .build();

            eventPublisher.publishEvent(auditEvent);
        } catch (Exception e) {
            log.warn("Falha ao capturar evento de auditoria: {}", e.getMessage());
        }

        return result;
    }

    private String getClientIpAddress() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "127.0.0.1";
    }

    private String extractTargetId(Object[] args, Object result) {
        if (result instanceof org.springframework.http.ResponseEntity<?> re && re.getBody() != null) {
            String idStr = tryGetIdFromObject(re.getBody());
            if (idStr != null) return idStr;
        }

        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg instanceof Number || arg instanceof String) {
                    String str = String.valueOf(arg);
                    if (!str.trim().isEmpty() && !"null".equalsIgnoreCase(str)) {
                        return str;
                    }
                }
                String idStr = tryGetIdFromObject(arg);
                if (idStr != null) return idStr;
            }
        }
        return null;
    }

    private String tryGetIdFromObject(Object obj) {
        if (obj == null) return null;
        try {
            var method = obj.getClass().getMethod("getId");
            Object idVal = method.invoke(obj);
            if (idVal != null) {
                String str = String.valueOf(idVal);
                if (!str.trim().isEmpty() && !"null".equalsIgnoreCase(str)) {
                    return str;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractTargetName(Object[] args, Object result, String targetType) {
        String nameFromObj = extractNameFromArgsOrResult(args, result);
        if (nameFromObj != null && !nameFromObj.trim().isEmpty()) {
            return nameFromObj;
        }

        String targetId = extractTargetId(args, result);
        if (targetId != null && !targetId.trim().isEmpty() && !"null".equalsIgnoreCase(targetId)) {
            return targetType + " #" + targetId;
        }

        return targetType;
    }

    private String extractNameFromArgsOrResult(Object[] args, Object result) {
        if (result instanceof org.springframework.http.ResponseEntity<?> re && re.getBody() != null) {
            String name = tryGetNameFromObject(re.getBody());
            if (name != null) return name;
        }

        if (args != null) {
            for (Object arg : args) {
                if (arg != null) {
                    String name = tryGetNameFromObject(arg);
                    if (name != null) return name;
                }
            }
        }
        return null;
    }

    private String tryGetNameFromObject(Object obj) {
        if (obj == null) return null;
        String[] getterNames = {"getName", "getUsername", "getRoomName", "getUnitName", "getTitle"};
        for (String getterName : getterNames) {
            try {
                var method = obj.getClass().getMethod(getterName);
                Object val = method.invoke(obj);
                if (val instanceof String s && !s.trim().isEmpty()) {
                    return s;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
