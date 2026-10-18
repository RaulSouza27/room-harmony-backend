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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/holidays")
public class HolidaysController {

  private final HolidayRepository holidayRepository;
  private final ReservationRepository reservationRepository;
  private final RoomRepository roomRepository;
  private final UnitRepository unitRepository;

  public HolidaysController(HolidayRepository holidayRepository,
                            ReservationRepository reservationRepository,
                            RoomRepository roomRepository,
                            UnitRepository unitRepository) {
    this.holidayRepository = holidayRepository;
    this.reservationRepository = reservationRepository;
    this.roomRepository = roomRepository;
    this.unitRepository = unitRepository;
  }

  private boolean isAdmin() {
    Authentication auth =
        SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream().anyMatch(
                               a
                               -> "admin".equals(a.getAuthority()) ||
                                      "ROLE_ADMIN".equals(a.getAuthority()));
  }

  @GetMapping
  public ResponseEntity<List<HolidayDTO>>
  getAllHolidays(@RequestParam(required = false) Integer unitId,
                 @RequestParam(required = false) Boolean activeOnly) {
    List<Holiday> holidays;
    if (Boolean.TRUE.equals(activeOnly)) {
      holidays = holidayRepository.findByStatusTrue();
    } else {
      holidays = holidayRepository.findAll();
    }

    if (unitId != null) {
      holidays =
          holidays.stream()
              .filter(
                  h -> h.getUnitId() == null || h.getUnitId().equals(unitId))
              .collect(Collectors.toList());
    }

    Map<Integer, String> unitMap = unitRepository.findAll().stream().collect(
        Collectors.toMap(Unit::getId, Unit::getName, (a, b) -> a));

    List<HolidayDTO> dtos = new ArrayList<>();
    for (Holiday h : holidays) {
      String uName = h.getUnitId() != null ? unitMap.get(h.getUnitId())
                                           : "Todas as Unidades";
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
      uName = unitRepository.findById(h.getUnitId())
                  .map(Unit::getName)
                  .orElse("Unidade Desconhecida");
    }
    return ResponseEntity.ok(new HolidayDTO(h, uName));
  }

  @PostMapping
  public ResponseEntity<?> createHoliday(@RequestBody HolidayDTO dto) {
    if (!isAdmin()) {
      return ResponseEntity.status(403).body(
          "Acesso negado: Apenas administradores podem cadastrar feriados.");
    }

    if (dto.getName() == null || dto.getName().trim().isEmpty()) {
      return ResponseEntity.badRequest().body(
          "O nome do feriado/bloqueio é obrigatório.");
    }
    if (dto.getStartDate() == null) {
      return ResponseEntity.badRequest().body("A data inicial é obrigatória.");
    }
    LocalDate startDate = dto.getStartDate();
    LocalDate endDate = dto.getEndDate() != null ? dto.getEndDate() : startDate;

    if (endDate.isBefore(startDate)) {
      return ResponseEntity.badRequest().body(
          "A data final não pode ser anterior à data inicial.");
    }

    Holiday holiday = new Holiday();
    holiday.setName(dto.getName().trim());
    holiday.setStartDate(startDate);
    holiday.setEndDate(endDate);
    holiday.setUnitId(dto.getUnitId());
    holiday.setDescription(dto.getDescription());
    holiday.setStatus(dto.getStatus() != null ? dto.getStatus() : true);

    Holiday saved = holidayRepository.save(holiday);

    // Transition existing active reservations in this date range and unit to
    // "reagendar"
    if (Boolean.TRUE.equals(saved.getStatus())) {
      transitionReservationsToReschedule(saved.getUnitId(), startDate, endDate);
    }

    String uName = saved.getUnitId() != null
                       ? unitRepository.findById(saved.getUnitId())
                             .map(Unit::getName)
                             .orElse("Unidade Desconhecida")
                       : "Todas as Unidades";

    return ResponseEntity.ok(new HolidayDTO(saved, uName));
  }

  private void transitionReservationsToReschedule(Integer unitId,
                                                  LocalDate startDate,
                                                  LocalDate endDate) {
    List<Reservation> targetReservations =
        reservationRepository.findByDataBetweenAndStatusTrue(startDate,
                                                             endDate);

    java.util.Set<Integer> allowedRoomIds = null;
    if (unitId != null) {
      allowedRoomIds = roomRepository.findByUnitId(unitId)
                           .stream()
                           .map(Room::getId)
                           .collect(Collectors.toSet());
    }

    List<Reservation> toSave = new ArrayList<>();
    for (Reservation r : targetReservations) {
      boolean matchesUnit =
          (unitId == null) ||
          (allowedRoomIds != null && allowedRoomIds.contains(r.getRoomsId()));

      if (matchesUnit) {
        String comments = r.getComments();
        String motivoVal = "Feriado/Bloqueio cadastrado na unidade";
        String aprovadoVal = "";
        String recorrencia = "unica";

        if (comments != null && comments.contains("|")) {
          String[] parts = comments.split("\\|", -1);
          if (parts.length > 2 && !parts[2].isEmpty())
            aprovadoVal = parts[2];
          if (parts.length > 3 && !parts[3].isEmpty())
            recorrencia = parts[3];
        }

        // Status muda para "reagendar"
        r.setComments("reagendar|" + motivoVal + "|" + aprovadoVal + "|" +
                      recorrencia);
        // Continua ativo no banco (status = true) para ser visível e tratado
        // nas pendências
        r.setStatus(true);
        toSave.add(r);
      }
    }

    if (!toSave.isEmpty()) {
      reservationRepository.saveAll(toSave);
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateHoliday(@PathVariable Integer id,
                                         @RequestBody HolidayDTO dto) {
    if (!isAdmin()) {
      return ResponseEntity.status(403).body(
          "Acesso negado: Apenas administradores podem editar feriados.");
    }

    Optional<Holiday> hOpt = holidayRepository.findById(id);
    if (hOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Holiday holiday = hOpt.get();

    LocalDate startDate = dto.getStartDate() != null ? dto.getStartDate()
                                                     : holiday.getStartDate();
    LocalDate endDate =
        dto.getEndDate() != null ? dto.getEndDate() : holiday.getEndDate();
    if (endDate.isBefore(startDate)) {
      return ResponseEntity.badRequest().body(
          "A data final não pode ser anterior à data inicial.");
    }

    Integer targetUnitId =
        dto.getUnitId() != null ? dto.getUnitId() : holiday.getUnitId();

    if (dto.getName() != null)
      holiday.setName(dto.getName().trim());
    holiday.setStartDate(startDate);
    holiday.setEndDate(endDate);
    holiday.setUnitId(targetUnitId);
    if (dto.getDescription() != null)
      holiday.setDescription(dto.getDescription());
    if (dto.getStatus() != null)
      holiday.setStatus(dto.getStatus());

    Holiday saved = holidayRepository.save(holiday);

    if (Boolean.TRUE.equals(saved.getStatus())) {
      transitionReservationsToReschedule(saved.getUnitId(), startDate, endDate);
    }

    String uName = saved.getUnitId() != null
                       ? unitRepository.findById(saved.getUnitId())
                             .map(Unit::getName)
                             .orElse("Unidade Desconhecida")
                       : "Todas as Unidades";

    return ResponseEntity.ok(new HolidayDTO(saved, uName));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteHoliday(@PathVariable Integer id) {
    if (!isAdmin()) {
      return ResponseEntity.status(403).body(
          "Acesso negado: Apenas administradores podem excluir feriados.");
    }
    if (!holidayRepository.existsById(id)) {
      return ResponseEntity.notFound().build();
    }
    holidayRepository.deleteById(id);
    return ResponseEntity.ok("Feriado/bloqueio excluído com sucesso.");
  }
}
