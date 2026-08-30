package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.ReservationDTO;
import com.clinica.escuta.model.Reservation;
import com.clinica.escuta.repository.ReservationRepository;
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

    public ReservationsController(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @PostMapping
    public ResponseEntity<?> createReservation(@RequestBody ReservationDTO request) {
        if (request.getRoomsId() == null || request.getUserId() == null || request.getData() == null ||
            request.getHoraInicio() == null || request.getHoraFim() == null) {
            return ResponseEntity.badRequest().body("Missing required reservation fields.");
        }

        // Check if creator is admin from SecurityContext
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isCreatorAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "admin".equals(a.getAuthority()));

        // If the creator is not admin, require depositImage (receipt)
        if (!isCreatorAdmin && (request.getDepositImage() == null || request.getDepositImage().trim().isEmpty() || "empty".equalsIgnoreCase(request.getDepositImage().trim()))) {
            return ResponseEntity.badRequest().body("O comprovante de pagamento é obrigatório.");
        }

        // Validate clinic hours
        if (!isValidWorkingHours(request.getData(), request.getHoraInicio(), request.getHoraFim())) {
            return ResponseEntity.badRequest().body("A reserva está fora do horário de funcionamento da clínica.");
        }

        // Determine recurrence string (default to "unica")
        String recorrencia = request.getRecorrencia() != null ? request.getRecorrencia() : "unica";

        // Validate duration
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
                reservation.setUserId(request.getUserId());
                reservation.setData(current);
                reservation.setHoraInicio(request.getHoraInicio());
                reservation.setHoraFim(request.getHoraFim());
                reservation.setDepositImage(request.getDepositImage() != null && !request.getDepositImage().isEmpty() ? request.getDepositImage() : "empty");
                reservation.setDescription(request.getDescription());
                
                String statusVal = request.getStatusString() != null ? request.getStatusString() : "pendente";
                if (isCreatorAdmin) {
                    statusVal = "aprovada";
                }
                String motivoVal = request.getMotivoNegacao() != null ? request.getMotivoNegacao() : "";
                String aprovadoVal = request.getAprovadoPor() != null ? request.getAprovadoPor() : "";
                if (isCreatorAdmin && (aprovadoVal == null || aprovadoVal.isEmpty())) {
                    aprovadoVal = auth != null ? auth.getName() : "admin";
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
        return ResponseEntity.ok(new ReservationDTO(savedList.get(0)));
    }

    @GetMapping("/readAll")
    public ResponseEntity<List<ReservationDTO>> readAll() {
        List<Reservation> all = reservationRepository.findAll();
        List<ReservationDTO> list = new ArrayList<>();
        for (Reservation r : all) {
            list.add(new ReservationDTO(r));
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        return reservationRepository.findById(id)
                .map(r -> ResponseEntity.ok(new ReservationDTO(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReservation(@PathVariable Integer id, @RequestBody ReservationDTO request) {
        Optional<Reservation> reservationOpt = reservationRepository.findById(id);
        if (reservationOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Reservation r = reservationOpt.get();
        
        LocalDate targetDate = request.getData() != null ? request.getData() : r.getData();
        LocalTime targetStart = request.getHoraInicio() != null ? request.getHoraInicio() : r.getHoraInicio();
        LocalTime targetEnd = request.getHoraFim() != null ? request.getHoraFim() : r.getHoraFim();
        
        String recorrencia = request.getRecorrencia();
        if (recorrencia == null) {
            recorrencia = getRecorrenciaFromComments(r.getComments());
        }

        // Validate clinic hours
        if (!isValidWorkingHours(targetDate, targetStart, targetEnd)) {
            return ResponseEntity.badRequest().body("A reserva está fora do horário de funcionamento da clínica.");
        }

        // Validate duration
        if ("semanal_anual".equals(recorrencia) || "turno".equals(recorrencia)) {
            if (!targetStart.plusHours(4).equals(targetEnd)) {
                return ResponseEntity.badRequest().body("Turnos devem ter a duração exata de 4 horas.");
            }
        } else {
            if (!targetStart.plusHours(1).equals(targetEnd)) {
                return ResponseEntity.badRequest().body("Reservas avulsas devem ter a duração exata de 1 hora.");
            }
        }

        // Check conflict
        Integer targetRoomId = request.getRoomsId() != null ? request.getRoomsId() : r.getRoomsId();
        if (hasConflict(targetRoomId, targetDate, targetStart, targetEnd, id)) {
            return ResponseEntity.badRequest().body("Existe um conflito de horário no dia " + targetDate + " na sala selecionada.");
        }

        if (request.getRoomsId() != null) r.setRoomsId(request.getRoomsId());
        if (request.getUserId() != null) r.setUserId(request.getUserId());
        if (request.getData() != null) r.setData(request.getData());
        if (request.getHoraInicio() != null) r.setHoraInicio(request.getHoraInicio());
        if (request.getHoraFim() != null) r.setHoraFim(request.getHoraFim());
        if (request.getDepositImage() != null) r.setDepositImage(request.getDepositImage());
        if (request.getDescription() != null) r.setDescription(request.getDescription());
        
        String statusVal = request.getStatusString() != null ? request.getStatusString() : getStatusFromComments(r.getComments());
        String motivoVal = request.getMotivoNegacao() != null ? request.getMotivoNegacao() : getMotivoFromComments(r.getComments());
        String aprovadoVal = request.getAprovadoPor() != null ? request.getAprovadoPor() : getAprovadoFromComments(r.getComments());
        
        r.setComments(statusVal + "|" + motivoVal + "|" + aprovadoVal + "|" + recorrencia);
        r.setStatus(!"cancelada".equals(statusVal) && !"negada".equals(statusVal));

        Reservation saved = reservationRepository.save(r);
        return ResponseEntity.ok(new ReservationDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReservation(@PathVariable Integer id) {
        if (!reservationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        reservationRepository.deleteById(id);
        return ResponseEntity.ok("Reservation deleted successfully.");
    }

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

    private boolean isValidWorkingHours(LocalDate date, LocalTime start, LocalTime end) {
        if (start.isAfter(end) || start.equals(end)) {
            return false;
        }
        
        java.time.DayOfWeek day = date.getDayOfWeek();
        if (day == java.time.DayOfWeek.SUNDAY) {
            return false;
        }
        
        LocalTime clinicStart = LocalTime.of(7, 0);
        if (day == java.time.DayOfWeek.SATURDAY) {
            LocalTime clinicEnd = LocalTime.of(19, 0);
            return !start.isBefore(clinicStart) && !end.isAfter(clinicEnd);
        } else {
            LocalTime clinicEnd = LocalTime.of(22, 0);
            return !start.isBefore(clinicStart) && !end.isAfter(clinicEnd);
        }
    }

    private boolean hasConflict(Integer roomId, LocalDate date, LocalTime start, LocalTime end, Integer ignoreId) {
        List<Reservation> activeReservations = reservationRepository.findByStatusTrue();
        for (Reservation r : activeReservations) {
            if (r.getId().equals(ignoreId)) {
                continue;
            }
            if (r.getRoomsId().equals(roomId) && r.getData().equals(date)) {
                if (start.isBefore(r.getHoraFim()) && r.getHoraInicio().isBefore(end)) {
                    return true;
                }
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
