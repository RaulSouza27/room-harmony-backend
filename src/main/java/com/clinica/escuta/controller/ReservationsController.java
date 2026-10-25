package com.clinica.escuta.controller;

import com.clinica.escuta.annotation.Auditable;
import com.clinica.escuta.DTO.ReservationDTO;
import com.clinica.escuta.model.Reservation;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.ReservationRepository;
import com.clinica.escuta.repository.UserRepository;
import com.clinica.escuta.service.TelegramNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/reservations")
public class ReservationsController {

    private final ReservationRepository reservationRepository;
    private final com.clinica.escuta.repository.RoomRepository roomRepository;
    private final com.clinica.escuta.repository.UnitRepository unitRepository;
    private final com.clinica.escuta.repository.HolidayRepository holidayRepository;
    private final TelegramNotificationService telegramNotificationService;
    private final UserRepository userRepository;

    public ReservationsController(
            ReservationRepository reservationRepository,
            com.clinica.escuta.repository.RoomRepository roomRepository,
            com.clinica.escuta.repository.UnitRepository unitRepository,
            com.clinica.escuta.repository.HolidayRepository holidayRepository,
            TelegramNotificationService telegramNotificationService,
            UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.unitRepository = unitRepository;
        this.holidayRepository = holidayRepository;
        this.telegramNotificationService = telegramNotificationService;
        this.userRepository = userRepository;
    }

    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "admin".equals(a.getAuthority()));
    }

    /** Resolve o id do usuário autenticado a partir do username guardado no JWT. */
    private Integer getAuthenticatedUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).map(User::getId).orElse(null);
    }

    @Auditable(action = "SOLICITACAO_RESERVA", targetType = "RESERVA")
    @PostMapping
    public ResponseEntity<?> createReservation(@RequestBody ReservationDTO request) {
        if (request.getRoomsId() == null || request.getUserId() == null || request.getData() == null ||
            request.getHoraInicio() == null || request.getHoraFim() == null) {
            return ResponseEntity.badRequest().body("Missing required reservation fields.");
        }

        // Check if creator is admin from SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isCreatorAdmin = isAdmin(auth);

        // Non-admins can only create reservations for themselves
        Integer authenticatedUserId = null;
        if (!isCreatorAdmin) {
            authenticatedUserId = getAuthenticatedUserId(auth);
            if (authenticatedUserId == null) {
                return ResponseEntity.status(403).body("Usuário autenticado não encontrado.");
            }
        }
        Integer ownerUserId = isCreatorAdmin ? request.getUserId() : authenticatedUserId;

        // If the creator is not admin, require depositImage (receipt)
        if (!isCreatorAdmin && (request.getDepositImage() == null || request.getDepositImage().trim().isEmpty() || "empty".equalsIgnoreCase(request.getDepositImage().trim()))) {
            return ResponseEntity.badRequest().body("O comprovante de pagamento é obrigatório.");
        }

        // Validate clinic hours
        if (!isValidWorkingHours(request.getRoomsId(), request.getData(), request.getHoraInicio(), request.getHoraFim())) {
            return ResponseEntity.badRequest().body("A reserva está fora do horário de funcionamento da unidade.");
        }

        // Determine recurrence string (default to "unica")
        String recorrencia = request.getRecorrencia() != null ? request.getRecorrencia() : "unica";

        // Validate duration (bypassed for Admins so they can create generic special reservations with any duration)
        if (!isCreatorAdmin) {
            if ("semanal_anual".equals(recorrencia) || "turno".equals(recorrencia)) {
                if (!request.getHoraInicio().plusHours(4).equals(request.getHoraFim())) {
                    return ResponseEntity.badRequest().body("Turnos devem ter a duração exata de 4 horas.");
                }
            } else {
                // "unica" or "semanal_mensal" / "avulsa_fixa"
                if (!request.getHoraInicio().plusHours(1).equals(request.getHoraFim())) {
                    return ResponseEntity.badRequest().body("Reservas avulsas devem ter a duração exata de 1 hora.");
                }
            }
        }

        LocalDate startDate = request.getData();
        LocalDate endDate;

        if ("semanal_mensal".equals(recorrencia) || "semanal".equals(recorrencia)) {
            endDate = startDate.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        } else if ("semanal_anual".equals(recorrencia) || "turno".equals(recorrencia)) {
            endDate = startDate.with(java.time.temporal.TemporalAdjusters.lastDayOfYear());
        } else {
            endDate = startDate;
        }

        // Dry-run / Pre-validation for conflicts on all target dates
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (current.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                if (hasConflict(request.getRoomsId(), current, request.getHoraInicio(), request.getHoraFim(), -1)) {
                    return ResponseEntity.badRequest().body("Existe um conflito de horário no dia " + current + " na sala selecionada.");
                }
            }
            if (endDate.equals(startDate)) {
                break;
            }
            current = current.plusWeeks(1);
        }

        // Persist all reservations
        List<Reservation> savedList = new ArrayList<>();
        current = startDate;
        while (!current.isAfter(endDate)) {
            if (current.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                Reservation reservation = new Reservation();
                reservation.setRoomsId(request.getRoomsId());
                reservation.setUserId(ownerUserId);
                reservation.setData(current);
                reservation.setHoraInicio(request.getHoraInicio());
                reservation.setHoraFim(request.getHoraFim());
                reservation.setDepositImage(request.getDepositImage() != null && !request.getDepositImage().isEmpty() ? request.getDepositImage() : "empty");
                reservation.setDescription(request.getDescription());
                
                // SECURITY: status/approval are decided by the server, never trusted from the client.
                String statusVal;
                String motivoVal;
                String aprovadoVal;
                if (isCreatorAdmin) {
                    statusVal = "aprovada";
                    motivoVal = request.getMotivoNegacao() != null ? request.getMotivoNegacao() : "";
                    aprovadoVal = request.getAprovadoPor() != null && !request.getAprovadoPor().isEmpty()
                            ? request.getAprovadoPor()
                            : (auth != null ? auth.getName() : "admin");
                } else {
                    statusVal = "pendente";
                    motivoVal = "";
                    aprovadoVal = "";
                }
                
                reservation.setComments(statusVal + "|" + motivoVal + "|" + aprovadoVal + "|" + recorrencia);
                reservation.setStatus(!"cancelada".equals(statusVal) && !"negada".equals(statusVal));
                
                savedList.add(reservationRepository.save(reservation));
            }
            if (endDate.equals(startDate)) {
                break;
            }
            current = current.plusWeeks(1);
        }

        // Return the first saved reservation
        if (savedList.isEmpty()) {
            return ResponseEntity.badRequest().body("Nenhuma reserva pôde ser gerada.");
        }

        Integer createdUnitId = roomRepository.findById(savedList.get(0).getRoomsId())
                .map(com.clinica.escuta.model.Room::getUnitId)
                .orElse(null);
        ReservationDTO resultDTO = new ReservationDTO(savedList.get(0), true, createdUnitId);
        boolean isSpecial = "reserva_especial_admin".equalsIgnoreCase(request.getDepositImage());
        telegramNotificationService.notifyNewReservation(resultDTO, isSpecial);

        return ResponseEntity.ok(resultDTO);
    }

    @GetMapping("/readAll")
    public ResponseEntity<List<ReservationDTO>> readAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer roomId,
            @RequestParam(required = false) Integer unitId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean includeReceipt
    ) {
        List<Reservation> all;
        if (startDate != null && endDate != null) {
            all = reservationRepository.findByDataBetween(startDate, endDate);
        } else if (userId != null) {
            all = reservationRepository.findByUserId(userId);
        } else if (roomId != null) {
            all = reservationRepository.findByRoomsId(roomId);
        } else {
            all = reservationRepository.findAll();
        }

        // Preload roomId -> unitId map in a single query to avoid N+1 roundtrips to remote DB
        java.util.Map<Integer, Integer> roomUnitMap = new java.util.HashMap<>();
        for (com.clinica.escuta.model.Room room : roomRepository.findAll()) {
            roomUnitMap.put(room.getId(), room.getUnitId());
        }

        List<ReservationDTO> list = new ArrayList<>();
        for (Reservation r : all) {
            Integer resUnitId = roomUnitMap.get(r.getRoomsId());

            if (unitId != null) {
                if (resUnitId == null || !resUnitId.equals(unitId)) {
                    continue;
                }
            }

            ReservationDTO dto = new ReservationDTO(r, includeReceipt, resUnitId);

            if (status != null && !status.trim().isEmpty()) {
                if (!status.equalsIgnoreCase(dto.getStatusString())) {
                    continue;
                }
            }

            if (roomId != null && !r.getRoomsId().equals(roomId)) {
                continue;
            }

            if (userId != null && !r.getUserId().equals(userId)) {
                continue;
            }

            list.add(dto);
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<?> getReceipt(@PathVariable Integer id) {
        Optional<Reservation> reservationOpt = reservationRepository.findById(id);
        if (reservationOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Reservation r = reservationOpt.get();
        java.util.Map<String, String> result = new java.util.HashMap<>();
        result.put("id", String.valueOf(r.getId()));
        result.put("depositImage", r.getDepositImage() != null ? r.getDepositImage() : "empty");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        return reservationRepository.findById(id)
                .map(r -> {
                    Integer uId = roomRepository.findById(r.getRoomsId()).map(com.clinica.escuta.model.Room::getUnitId).orElse(null);
                    return ResponseEntity.ok(new ReservationDTO(r, true, uId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Auditable(action = "ALTERACAO_RESERVA", targetType = "RESERVA")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateReservation(@PathVariable Integer id, @RequestBody ReservationDTO request) {
        Optional<Reservation> reservationOpt = reservationRepository.findById(id);
        if (reservationOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Reservation r = reservationOpt.get();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isUpdaterAdmin = isAdmin(auth);

        if (!isUpdaterAdmin) {
            // Non-admins may only touch their own reservations
            Integer authenticatedUserId = getAuthenticatedUserId(auth);
            if (authenticatedUserId == null || !authenticatedUserId.equals(r.getUserId())) {
                return ResponseEntity.status(403).body("Você não tem permissão para alterar esta reserva.");
            }
            // Non-admins may only request cancellation or cancel (pending) reservations
            String requestedStatus = request.getStatusString();
            if (requestedStatus != null
                    && !"solicitacao_cancelamento".equals(requestedStatus)
                    && !"cancelada".equals(requestedStatus)) {
                return ResponseEntity.status(403).body("Locatários não podem alterar o status da reserva para '" + requestedStatus + "'.");
            }
        }

        // Validação da regra de 24h de antecedência para solicitação de cancelamento por locatário
        if ("solicitacao_cancelamento".equals(request.getStatusString()) && !isUpdaterAdmin) {
            java.time.LocalDateTime reservationStart = r.getData().atTime(r.getHoraInicio());
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (now.plusHours(24).isAfter(reservationStart)) {
                return ResponseEntity.badRequest().body("Solicitações de cancelamento só podem ser feitas com no mínimo 24 horas de antecedência.");
            }
        }

        if ("cancelada".equals(request.getStatusString()) && !isUpdaterAdmin && "aprovada".equals(getStatusFromComments(r.getComments()))) {
            return ResponseEntity.badRequest().body("Locatários não podem cancelar reservas aprovadas diretamente. Solicite o cancelamento ao administrador.");
        }
        
        boolean isDateTimeOrRoomChanged = (request.getData() != null && !request.getData().equals(r.getData()))
                || (request.getHoraInicio() != null && !request.getHoraInicio().equals(r.getHoraInicio()))
                || (request.getHoraFim() != null && !request.getHoraFim().equals(r.getHoraFim()))
                || (request.getRoomsId() != null && !request.getRoomsId().equals(r.getRoomsId()));

        LocalDate targetDate = request.getData() != null ? request.getData() : r.getData();
        LocalTime targetStart = request.getHoraInicio() != null ? request.getHoraInicio() : r.getHoraInicio();
        LocalTime targetEnd = request.getHoraFim() != null ? request.getHoraFim() : r.getHoraFim();
        
        String recorrencia = request.getRecorrencia();
        if (recorrencia == null) {
            recorrencia = getRecorrenciaFromComments(r.getComments());
        }

        Integer targetRoomId = request.getRoomsId() != null ? request.getRoomsId() : r.getRoomsId();

        if (isDateTimeOrRoomChanged) {
            // Validate clinic hours
            if (!isValidWorkingHours(targetRoomId, targetDate, targetStart, targetEnd)) {
                return ResponseEntity.badRequest().body("A reserva está fora do horário de funcionamento da unidade.");
            }

            // Validate duration
            if (!isUpdaterAdmin) {
                if ("semanal_anual".equals(recorrencia) || "turno".equals(recorrencia)) {
                    if (!targetStart.plusHours(4).equals(targetEnd)) {
                        return ResponseEntity.badRequest().body("Turnos devem ter a duração exata de 4 horas.");
                    }
                } else {
                    if (!targetStart.plusHours(1).equals(targetEnd)) {
                        return ResponseEntity.badRequest().body("Reservas avulsas devem ter a duração exata de 1 hora.");
                    }
                }
            }

            // Check conflict
            if (hasConflict(targetRoomId, targetDate, targetStart, targetEnd, id)) {
                return ResponseEntity.badRequest().body("Existe um conflito de horário no dia " + targetDate + " na sala selecionada.");
            }
        }

        if (request.getRoomsId() != null) r.setRoomsId(request.getRoomsId());
        if (isUpdaterAdmin && request.getUserId() != null) r.setUserId(request.getUserId());
        if (request.getData() != null) r.setData(request.getData());
        if (request.getHoraInicio() != null) r.setHoraInicio(request.getHoraInicio());
        if (request.getHoraFim() != null) r.setHoraFim(request.getHoraFim());
        if (request.getDepositImage() != null) r.setDepositImage(request.getDepositImage());
        if (request.getDescription() != null) r.setDescription(request.getDescription());
        
        String statusVal = request.getStatusString() != null ? request.getStatusString() : getStatusFromComments(r.getComments());
        String motivoVal;
        String aprovadoVal;
        if (isUpdaterAdmin) {
            motivoVal = request.getMotivoNegacao() != null ? request.getMotivoNegacao() : getMotivoFromComments(r.getComments());
            aprovadoVal = request.getAprovadoPor() != null ? request.getAprovadoPor() : getAprovadoFromComments(r.getComments());
        } else {
            // Non-admins cannot forge approval data
            motivoVal = getMotivoFromComments(r.getComments());
            aprovadoVal = getAprovadoFromComments(r.getComments());
            // Any date/time/room change by a tenant requires new admin approval
            if (isDateTimeOrRoomChanged && request.getStatusString() == null) {
                statusVal = "pendente";
                aprovadoVal = "";
            }
        }
        
        r.setComments(statusVal + "|" + motivoVal + "|" + aprovadoVal + "|" + recorrencia);
        r.setStatus(!"cancelada".equals(statusVal) && !"negada".equals(statusVal));

        Reservation saved = reservationRepository.save(r);
        Integer savedUnitId = roomRepository.findById(saved.getRoomsId()).map(com.clinica.escuta.model.Room::getUnitId).orElse(null);
        return ResponseEntity.ok(new ReservationDTO(saved, true, savedUnitId));
    }

    @Auditable(action = "EXCLUSAO_RESERVA", targetType = "RESERVA")
    @PreAuthorize("hasAuthority('admin')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReservation(@PathVariable Integer id) {
        if (!reservationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        reservationRepository.deleteById(id);
        return ResponseEntity.ok("Reservation deleted successfully.");
    }

    @Auditable(action = "EXCLUSAO_LOTE_RESERVA", targetType = "RESERVA")
    @PreAuthorize("hasAuthority('admin')")
    @PostMapping("/delete-batch")
    public ResponseEntity<?> deleteReservationsBatch(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("No IDs provided.");
        }
        for (Integer id : ids) {
            if (reservationRepository.existsById(id)) {
                reservationRepository.deleteById(id);
            }
        }
        return ResponseEntity.ok("Reservations deleted successfully.");
    }

    @Auditable(action = "CANCELAMENTO_LOTE_RESERVA", targetType = "RESERVA")
    @PreAuthorize("hasAuthority('admin')")
    @PostMapping("/cancel-batch")
    public ResponseEntity<?> cancelReservationsBatch(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body("No IDs provided.");
        }
        List<Reservation> reservations = reservationRepository.findAllById(ids);
        List<Reservation> toUpdate = new ArrayList<>();
        for (Reservation r : reservations) {
            String currentStatus = getStatusFromComments(r.getComments());
            if (!"cancelada".equalsIgnoreCase(currentStatus)) {
                String motivoVal = getMotivoFromComments(r.getComments());
                String aprovadoVal = getAprovadoFromComments(r.getComments());
                String recorrencia = getRecorrenciaFromComments(r.getComments());
                r.setComments("cancelada|" + motivoVal + "|" + aprovadoVal + "|" + recorrencia);
                r.setStatus(false);
                toUpdate.add(r);
            }
        }
        if (!toUpdate.isEmpty()) {
            reservationRepository.saveAll(toUpdate);
        }
        return ResponseEntity.ok("Reservations cancelled successfully.");
    }

    private boolean isValidWorkingHours(Integer roomId, LocalDate date, LocalTime start, LocalTime end) {
        if (start == null || end == null || start.isAfter(end) || start.equals(end) || roomId == null || date == null) {
            return false;
        }

        Optional<com.clinica.escuta.model.Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return false;
        }
        com.clinica.escuta.model.Room room = roomOpt.get();

        Optional<com.clinica.escuta.model.Unit> unitOpt = unitRepository.findById(room.getUnitId());
        if (unitOpt.isEmpty()) {
            return false;
        }
        com.clinica.escuta.model.Unit unit = unitOpt.get();

        // Check if date falls on an active holiday for this unit or global holiday
        List<com.clinica.escuta.model.Holiday> activeHolidays = holidayRepository.findByStatusTrue();
        for (com.clinica.escuta.model.Holiday h : activeHolidays) {
            if (!date.isBefore(h.getStartDate()) && !date.isAfter(h.getEndDate())) {
                if (h.getUnitId() == null || h.getUnitId().equals(unit.getId())) {
                    return false;
                }
            }
        }

        java.util.Map<String, ?> hours = unit.getBusinessHours();
        if (hours == null || hours.isEmpty()) {
            hours = com.clinica.escuta.DTO.UnitDTO.createDefaultBusinessHours();
        }

        int dayIndex = date.getDayOfWeek().getValue(); // Monday=1 ... Sunday=7
        if (dayIndex == 7) {
            dayIndex = 0; // 0 is Sunday in businessHours JSON
        }

        Object scheduleObj = hours.get(String.valueOf(dayIndex));
        com.clinica.escuta.DTO.DayScheduleDTO daySchedule = getDaySchedule(scheduleObj);
        if (daySchedule == null || !daySchedule.isAtivo() || daySchedule.getAbertura() == null || daySchedule.getFechamento() == null) {
            return false;
        }

        try {
            LocalTime abertura = LocalTime.parse(daySchedule.getAbertura().length() == 5 ? daySchedule.getAbertura() + ":00" : daySchedule.getAbertura());
            LocalTime fechamento = LocalTime.parse(daySchedule.getFechamento().length() == 5 ? daySchedule.getFechamento() + ":00" : daySchedule.getFechamento());
            return !start.isBefore(abertura) && !end.isAfter(fechamento);
        } catch (Exception e) {
            return false;
        }
    }

    private com.clinica.escuta.DTO.DayScheduleDTO getDaySchedule(Object scheduleObj) {
        if (scheduleObj == null) return null;
        if (scheduleObj instanceof com.clinica.escuta.DTO.DayScheduleDTO dto) {
            return dto;
        }
        if (scheduleObj instanceof java.util.Map<?, ?> map) {
            com.clinica.escuta.DTO.DayScheduleDTO dto = new com.clinica.escuta.DTO.DayScheduleDTO();
            Object ativo = map.get("ativo");
            if (ativo == null) ativo = map.get("active");
            if (ativo instanceof Boolean b) dto.setAtivo(b);
            else if (ativo instanceof String s) dto.setAtivo(Boolean.parseBoolean(s));

            Object abertura = map.get("abertura");
            if (abertura == null) abertura = map.get("opening");
            if (abertura != null) dto.setAbertura(String.valueOf(abertura));

            Object fechamento = map.get("fechamento");
            if (fechamento == null) fechamento = map.get("closing");
            if (fechamento != null) dto.setFechamento(String.valueOf(fechamento));

            return dto;
        }
        return null;
    }

    private boolean hasConflict(Integer roomId, LocalDate date, LocalTime start, LocalTime end, Integer ignoreId) {
        List<Reservation> activeReservations = reservationRepository.findByRoomsIdAndDataAndStatusTrue(roomId, date);
        for (Reservation r : activeReservations) {
            if (r.getId().equals(ignoreId)) {
                continue;
            }
            if (start.isBefore(r.getHoraFim()) && r.getHoraInicio().isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private String getStatusFromComments(String comments) {
        if (comments != null && comments.contains("|")) {
            String[] parts = comments.split("\\|", -1);
            if (parts.length > 0 && !parts[0].isEmpty()) return parts[0];
        }
        return "pendente";
    }

    private String getMotivoFromComments(String comments) {
        if (comments != null && comments.contains("|")) {
            String[] parts = comments.split("\\|", -1);
            if (parts.length > 1) return parts[1];
        }
        return "";
    }

    private String getAprovadoFromComments(String comments) {
        if (comments != null && comments.contains("|")) {
            String[] parts = comments.split("\\|", -1);
            if (parts.length > 2) return parts[2];
        }
        return "";
    }

    private String getRecorrenciaFromComments(String comments) {
        if (comments != null && comments.contains("|")) {
            String[] parts = comments.split("\\|", -1);
            if (parts.length > 3 && !parts[3].isEmpty()) {
                return parts[3];
            }
        }
        return "unica";
    }
}
