package com.clinica.escuta.aspect;

import com.clinica.escuta.DTO.ReservationDTO;
import com.clinica.escuta.annotation.Auditable;
import com.clinica.escuta.event.AuditEvent;
import com.clinica.escuta.model.Room;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.RoomRepository;
import com.clinica.escuta.repository.UnitRepository;
import com.clinica.escuta.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

  private final ApplicationEventPublisher eventPublisher;
  private final UserRepository userRepository;
  private final RoomRepository roomRepository;
  private final UnitRepository unitRepository;

  @Around("@annotation(auditable)")
  public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable)
      throws Throwable {
    Object result = joinPoint.proceed();

    // Não auditar respostas de erro (4xx ou 5xx)
    if (result instanceof ResponseEntity<?> re &&
        !re.getStatusCode().is2xxSuccessful()) {
      return result;
    }

    try {
      String username = null;
      String userRole = null;
      Authentication auth =
          SecurityContextHolder.getContext().getAuthentication();

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
      String targetName =
          extractTargetName(args, result, auditable.targetType());
      String action = auditable.action();
      String details =
          auditable.details().isEmpty() ? null : auditable.details();

      // Enriquecimento automático para operações de RESERVA:
      // inclui sempre Quem Fez a Reserva (Profissional/Locatário) e Quem
      // Aprovou/Executou
      if (result instanceof ResponseEntity<?> re && re.getBody() instanceof
                                                        ReservationDTO resDto) {
        String profInfo = resolveUserSummary(resDto.getUserId());
        String roomInfo = resolveRoomAndUnitSummary(resDto.getRoomsId());
        String dateStr = resDto.getData() != null
                             ? resDto.getData().format(
                                   DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                             : "-";
        String timeStr =
            (resDto.getHoraInicio() != null
                 ? resDto.getHoraInicio().toString().substring(
                       0,
                       Math.min(5, resDto.getHoraInicio().toString().length()))
                 : "") +
            " às " +
            (resDto.getHoraFim() != null
                 ? resDto.getHoraFim().toString().substring(
                       0, Math.min(5, resDto.getHoraFim().toString().length()))
                 : "");

        String status = resDto.getStatusString() != null
                            ? resDto.getStatusString()
                            : "pendente";
        String aprovadorRaw = resDto.getAprovadoPor();
        String aprovadorInfo =
            resolveApproverSummary(aprovadorRaw, userName, userEmail, status);

        // Refinar a action quando for criação ou alteração de reserva
        if ("SOLICITACAO_RESERVA".equals(action) &&
            "reserva_especial_admin".equalsIgnoreCase(
                resDto.getDepositImage())) {
          action = "RESERVA_ESPECIAL_CRIADA";
        } else if ("ALTERACAO_RESERVA".equals(action)) {
          ReservationDTO reqDto = findReservationDtoArg(args);
          String requestedStatus =
              reqDto != null ? reqDto.getStatusString() : null;
          if ("aprovada".equalsIgnoreCase(requestedStatus)) {
            action = "APROVACAO_RESERVA";
          } else if ("negada".equalsIgnoreCase(requestedStatus)) {
            action = "REJEICAO_RESERVA";
          } else if ("solicitacao_cancelamento".equalsIgnoreCase(
                         requestedStatus)) {
            action = "SOLICITACAO_CANCELAMENTO_RESERVA";
          } else if ("cancelada".equalsIgnoreCase(requestedStatus)) {
            action = "CANCELAMENTO_RESERVA";
          }
        }

        targetName = "Reserva #" + resDto.getId() + " — " + profInfo;

        StringBuilder sb = new StringBuilder();
        sb.append("Profissional (Quem reservou): ")
            .append(profInfo)
            .append("\n");
        sb.append("Aprovado por: ").append(aprovadorInfo).append("\n");
        sb.append("Executado por (Ator da ação): ")
            .append(userName != null ? userName : "Desconhecido")
            .append(userEmail != null ? " (" + userEmail + ")" : "")
            .append("\n");
        sb.append("Local: ").append(roomInfo).append("\n");
        sb.append("Data/Horário: ")
            .append(dateStr)
            .append(" das ")
            .append(timeStr)
            .append("\n");
        sb.append("Status resultante: ").append(status.toUpperCase());

        if (resDto.getMotivoNegacao() != null &&
            !resDto.getMotivoNegacao().isBlank()) {
          sb.append("\nMotivo/Observação de status: ")
              .append(resDto.getMotivoNegacao());
        }
        if (resDto.getDescription() != null &&
            !resDto.getDescription().isBlank()) {
          sb.append("\nObservações: ").append(resDto.getDescription());
        }

        details = sb.toString();
      }

      AuditEvent auditEvent =
          AuditEvent.builder()
              .userId(userId)
              .userName(userName != null ? userName : "Desconhecido")
              .userEmail(userEmail != null ? userEmail
                                           : "desconhecido@clinica.com")
              .userRole(userRole)
              .action(action)
              .targetType(auditable.targetType())
              .targetId(targetId)
              .targetName(targetName)
              .details(details)
              .ipAddress(ipAddress)
              .build();

      eventPublisher.publishEvent(auditEvent);
    } catch (Exception e) {
      log.warn("Falha ao capturar evento de auditoria: {}", e.getMessage());
    }

    return result;
  }

  private ReservationDTO findReservationDtoArg(Object[] args) {
    if (args == null)
      return null;
    for (Object arg : args) {
      if (arg instanceof ReservationDTO dto) {
        return dto;
      }
    }
    return null;
  }

  private String resolveUserSummary(Integer uid) {
    if (uid == null)
      return "Não identificado";
    return userRepository.findById(uid)
        .map(u -> u.getUsername() + " (" + u.getEmail() + ")")
        .orElse("Profissional #" + uid);
  }

  private String resolveApproverSummary(String aprovadorRaw, String actorName,
                                        String actorEmail, String status) {
    if (aprovadorRaw != null && !aprovadorRaw.isBlank()) {
      try {
        Integer apId = Integer.valueOf(aprovadorRaw.trim());
        Optional<User> apOpt = userRepository.findById(apId);
        if (apOpt.isPresent()) {
          User ap = apOpt.get();
          return ap.getUsername() + " (" + ap.getEmail() + ")";
        }
      } catch (NumberFormatException ignored) {
        Optional<User> apOpt =
            userRepository.findByUsername(aprovadorRaw.trim());
        if (apOpt.isPresent()) {
          User ap = apOpt.get();
          return ap.getUsername() + " (" + ap.getEmail() + ")";
        }
      }
      return aprovadorRaw;
    }
    if ("aprovada".equalsIgnoreCase(status) && actorName != null) {
      return actorName + (actorEmail != null ? " (" + actorEmail + ")" : "");
    }
    return "Pendente / Ainda não aprovada";
  }

  private String resolveRoomAndUnitSummary(Integer roomId) {
    if (roomId == null)
      return "Sala não informada";
    Optional<Room> roomOpt = roomRepository.findById(roomId);
    if (roomOpt.isEmpty())
      return "Sala #" + roomId;
    Room room = roomOpt.get();
    String unitName = unitRepository.findById(room.getUnitId())
                          .map(Unit::getName)
                          .orElse("Unidade #" + room.getUnitId());
    return room.getName() + " · " + unitName;
  }

  private String getClientIpAddress() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
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
    if (result instanceof org.springframework.http.ResponseEntity<?> re &&
        re.getBody() != null) {
      String idStr = tryGetIdFromObject(re.getBody());
      if (idStr != null)
        return idStr;
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
        if (idStr != null)
          return idStr;
      }
    }
    return null;
  }

  private String tryGetIdFromObject(Object obj) {
    if (obj == null)
      return null;
    try {
      var method = obj.getClass().getMethod("getId");
      Object idVal = method.invoke(obj);
      if (idVal != null) {
        String str = String.valueOf(idVal);
        if (!str.trim().isEmpty() && !"null".equalsIgnoreCase(str)) {
          return str;
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private String extractTargetName(Object[] args, Object result,
                                   String targetType) {
    String nameFromObj = extractNameFromArgsOrResult(args, result);
    if (nameFromObj != null && !nameFromObj.trim().isEmpty()) {
      return nameFromObj;
    }

    String targetId = extractTargetId(args, result);
    if (targetId != null && !targetId.trim().isEmpty() &&
        !"null".equalsIgnoreCase(targetId)) {
      return targetType + " #" + targetId;
    }

    return targetType;
  }

  private String extractNameFromArgsOrResult(Object[] args, Object result) {
    if (result instanceof org.springframework.http.ResponseEntity<?> re &&
        re.getBody() != null) {
      String name = tryGetNameFromObject(re.getBody());
      if (name != null)
        return name;
    }

    if (args != null) {
      for (Object arg : args) {
        if (arg != null) {
          String name = tryGetNameFromObject(arg);
          if (name != null)
            return name;
        }
      }
    }
    return null;
  }

  private String tryGetNameFromObject(Object obj) {
    if (obj == null)
      return null;
    String[] getterNames = {"getName",     "getUsername", "getRoomName",
                            "getUnitName", "getTitle",    "getProfission"};
    for (String getterName : getterNames) {
      try {
        var method = obj.getClass().getMethod(getterName);
        Object val = method.invoke(obj);
        if (val instanceof String s && !s.trim().isEmpty()) {
          return s;
        }
      } catch (Exception ignored) {
      }
    }
    return null;
  }
}
