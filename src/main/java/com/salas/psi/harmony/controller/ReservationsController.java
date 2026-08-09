package com.salas.psi.harmony.controller;

import com.salas.psi.harmony.DTO.ReservationDTO;
import com.salas.psi.harmony.model.Reservation;
import com.salas.psi.harmony.repository.ReservationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

        Reservation reservation = ReservationDTO.toEntity(request);
        Reservation saved = reservationRepository.save(reservation);
        return ResponseEntity.ok(new ReservationDTO(saved));
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
        if (request.getRoomsId() != null) r.setRoomsId(request.getRoomsId());
        if (request.getUserId() != null) r.setUserId(request.getUserId());
        if (request.getData() != null) r.setData(request.getData());
        if (request.getHoraInicio() != null) r.setHoraInicio(request.getHoraInicio());
        if (request.getHoraFim() != null) r.setHoraFim(request.getHoraFim());
        if (request.getDepositImage() != null) r.setDepositImage(request.getDepositImage());
        if (request.getDescription() != null) r.setDescription(request.getDescription());
        
        if (request.getStatusString() != null) {
            String statusVal = request.getStatusString();
            String motivoVal = request.getMotivoNegacao() != null ? request.getMotivoNegacao() : "";
            String aprovadoVal = request.getAprovadoPor() != null ? request.getAprovadoPor() : "";
            String recVal = request.getRecorrencia() != null ? request.getRecorrencia() : "unica";
            r.setComments(statusVal + "|" + motivoVal + "|" + aprovadoVal + "|" + recVal);
            r.setStatus(!"cancelada".equals(statusVal) && !"negada".equals(statusVal));
        }

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
}
