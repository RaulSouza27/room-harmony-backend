package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.HolidayDTO;
import com.clinica.escuta.model.Holiday;
import com.clinica.escuta.model.Reservation;
import com.clinica.escuta.model.Room;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.repository.HolidayRepository;
import com.clinica.escuta.repository.ReservationRepository;
import com.clinica.escuta.repository.RoomRepository;
import com.clinica.escuta.repository.UnitRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/holidays")
public class HolidaysController {

    private final HolidayRepository holidayRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final UnitRepository unitRepository;

    public HolidaysController(
            HolidayRepository holidayRepository,
            ReservationRepository reservationRepository,
            RoomRepository roomRepository,
            UnitRepository unitRepository) {
        this.holidayRepository = holidayRepository;
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.unitRepository = unitRepository;
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "admin".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping
    public ResponseEntity<List<HolidayDTO>> getAllHolidays(
            @RequestParam(required = false) Integer unitId,
            @RequestParam(required = false) Boolean activeOnly) {
        List<Holiday> holidays;
        if (Boolean.TRUE.equals(activeOnly)) {
            holidays = holidayRepository.findByStatusTrue();
        } else {
            holidays = holidayRepository.findAll();
        }

        if (unitId != null) {
            holidays = holidays.stream()
                    .filter(h -> h.getUnitId() == null || h.getUnitId().equals(unitId))
                    .collect(Collectors.toList());
        }

        Map<Integer, String> unitMap = unitRepository.findAll().stream()
                .collect(Collectors.toMap(Unit::getId, Unit::getName, (a, b) -> a));

        List<HolidayDTO> dtos = new ArrayList<>();
        for (Holiday h : holidays) {
            String uName = h.getUnitId() != null ? unitMap.get(h.getUnitId()) : "Todas as Unidades";
            dtos.add(new HolidayDTO(h, uName));
        }

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        Optional<Holiday> hOpt = holidayRepository.findById(id);
        if (hOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Holiday h = hOpt.get();
        String uName = "Todas as Unidades";
        if (h.getUnitId() != null) {
            uName = unitRepository.findById(h.getUnitId()).map(Unit::getName).orElse("Unidade Desconhecida");
        }
        return ResponseEntity.ok(new HolidayDTO(h, uName));
    }

    @PostMapping
    public ResponseEntity<?> createHoliday(@RequestBody HolidayDTO dto) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body("Acesso negado: Apenas administradores podem cadastrar feriados.");
        }

        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("O nome do feriado/bloqueio é obrigatório.");
        }
        if (dto.getStartDate() == null) {
            return ResponseEntity.badRequest().body("A data inicial é obrigatória.");
        }
        LocalDate startDate = dto.getStartDate();
        LocalDate endDate = dto.getEndDate() != null ? dto.getEndDate() : startDate;

        if (endDate.isBefore(startDate)) {
            return ResponseEntity.badRequest().body("A data final não pode ser anterior à data inicial.");
        }

        // Check conflicts with existing active reservations
        List<Reservation> activeReservations = reservationRepository.findByStatusTrue();
        List<String> conflicts = new ArrayList<>();

        for (Reservation r : activeReservations) {
            if (r.getData() != null && !r.getData().isBefore(startDate) && !r.getData().isAfter(endDate)) {
                if (dto.getUnitId() == null) {
                    conflicts.add("Reserva ID " + r.getId() + " no dia " + r.getData());
                } else {
                    Optional<Room> roomOpt = roomRepository.findById(r.getRoomsId());
                    if (roomOpt.isPresent() && dto.getUnitId().equals(roomOpt.get().getUnitId())) {
                        conflicts.add("Reserva ID " + r.getId() + " no dia " + r.getData());
                    }
                }
            }
        }

        if (!conflicts.isEmpty()) {
            return ResponseEntity.badRequest().body(
                "Não é possível cadastrar o feriado/bloqueio pois existem " + conflicts.size() +
                " reserva(s) agendada(s) no período selecionado. Por favor, mude ou cancele as reservas antes de cadastrar o bloqueio."
            );
        }

        Holiday holiday = new Holiday();
        holiday.setName(dto.getName().trim());
        holiday.setStartDate(startDate);
        holiday.setEndDate(endDate);
        holiday.setUnitId(dto.getUnitId());
        holiday.setDescription(dto.getDescription());
        holiday.setStatus(dto.getStatus() != null ? dto.getStatus() : true);

        Holiday saved = holidayRepository.save(holiday);

        String uName = saved.getUnitId() != null ?
                unitRepository.findById(saved.getUnitId()).map(Unit::getName).orElse("Unidade Desconhecida") : "Todas as Unidades";

        return ResponseEntity.ok(new HolidayDTO(saved, uName));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateHoliday(@PathVariable Integer id, @RequestBody HolidayDTO dto) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body("Acesso negado: Apenas administradores podem editar feriados.");
        }

        Optional<Holiday> hOpt = holidayRepository.findById(id);
        if (hOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Holiday holiday = hOpt.get();

        LocalDate startDate = dto.getStartDate() != null ? dto.getStartDate() : holiday.getStartDate();
        LocalDate endDate = dto.getEndDate() != null ? dto.getEndDate() : holiday.getEndDate();
        if (endDate.isBefore(startDate)) {
            return ResponseEntity.badRequest().body("A data final não pode ser anterior à data inicial.");
        }

        Integer targetUnitId = dto.getUnitId() != null ? dto.getUnitId() : holiday.getUnitId();

        // Conflict check
        List<Reservation> activeReservations = reservationRepository.findByStatusTrue();
        List<String> conflicts = new ArrayList<>();

        for (Reservation r : activeReservations) {
            if (r.getData() != null && !r.getData().isBefore(startDate) && !r.getData().isAfter(endDate)) {
                if (targetUnitId == null) {
                    conflicts.add("Reserva ID " + r.getId() + " no dia " + r.getData());
                } else {
                    Optional<Room> roomOpt = roomRepository.findById(r.getRoomsId());
                    if (roomOpt.isPresent() && targetUnitId.equals(roomOpt.get().getUnitId())) {
                        conflicts.add("Reserva ID " + r.getId() + " no dia " + r.getData());
                    }
                }
            }
        }

        if (!conflicts.isEmpty()) {
            return ResponseEntity.badRequest().body(
                "Não é possível atualizar o feriado/bloqueio pois existem " + conflicts.size() +
                " reserva(s) agendada(s) no período selecionado. Por favor, mude ou cancele as reservas antes de atualizar o bloqueio."
            );
        }

        if (dto.getName() != null) holiday.setName(dto.getName().trim());
        holiday.setStartDate(startDate);
        holiday.setEndDate(endDate);
        holiday.setUnitId(targetUnitId);
        if (dto.getDescription() != null) holiday.setDescription(dto.getDescription());
        if (dto.getStatus() != null) holiday.setStatus(dto.getStatus());

        Holiday saved = holidayRepository.save(holiday);

        String uName = saved.getUnitId() != null ?
                unitRepository.findById(saved.getUnitId()).map(Unit::getName).orElse("Unidade Desconhecida") : "Todas as Unidades";

        return ResponseEntity.ok(new HolidayDTO(saved, uName));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHoliday(@PathVariable Integer id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body("Acesso negado: Apenas administradores podem excluir feriados.");
        }
        if (!holidayRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        holidayRepository.deleteById(id);
        return ResponseEntity.ok("Feriado/bloqueio excluído com sucesso.");
    }
}
