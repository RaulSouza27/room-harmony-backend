package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.RoomDTO;
import com.clinica.escuta.model.Room;
import com.clinica.escuta.repository.RoomRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/rooms")
public class RoomsController {

    private final RoomRepository roomRepository;

    public RoomsController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @PreAuthorize("hasAuthority('admin')")
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody RoomDTO request) {
        if (request.getName() == null || request.getUnitId() == null) {
            return ResponseEntity.badRequest().body("Missing required fields (name, unitId).");
        }

        if (roomRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("Room name already exists.");
        }

        Room room = new Room();
        room.setName(request.getName());
        room.setUnitId(request.getUnitId());
        room.setStatus(request.isStatus());
        room.setDescription(request.getDescription());
        room.setComments(request.getComments());

        List<String> photos = request.getPhotos();
        room.setPhoto1(photos != null && photos.size() > 0 ? photos.get(0) : null);
        room.setPhoto2(photos != null && photos.size() > 1 ? photos.get(1) : null);
        room.setPhoto3(photos != null && photos.size() > 2 ? photos.get(2) : null);
        room.setPhoto4(photos != null && photos.size() > 3 ? photos.get(3) : null);

        Room saved = roomRepository.save(room);
        return ResponseEntity.ok(new RoomDTO(saved));
    }

    @GetMapping("/readAll")
    public ResponseEntity<List<RoomDTO>> readAll() {
        List<RoomDTO> list = new ArrayList<>();
        for (Room r : roomRepository.findByStatusTrue()) {
            list.add(new RoomDTO(r));
        }
        for (Room r : roomRepository.findByStatusFalse()) {
            list.add(new RoomDTO(r));
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRoomById(@PathVariable Integer id) {
        return roomRepository.findById(id)
                .map(room -> ResponseEntity.ok(new RoomDTO(room)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('admin')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoom(@PathVariable Integer id, @RequestBody RoomDTO request) {
        Optional<Room> roomOpt = roomRepository.findById(id);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Room room = roomOpt.get();

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            Optional<Room> existing = roomRepository.findByName(request.getName());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("Room name already exists.");
            }
            room.setName(request.getName());
        }

        if (request.getUnitId() != null) {
            room.setUnitId(request.getUnitId());
        }

        room.setStatus(request.isStatus());
        room.setDescription(request.getDescription());
        room.setComments(request.getComments());

        List<String> photos = request.getPhotos();
        if (photos != null) {
            room.setPhoto1(photos.size() > 0 ? photos.get(0) : null);
            room.setPhoto2(photos.size() > 1 ? photos.get(1) : null);
            room.setPhoto3(photos.size() > 2 ? photos.get(2) : null);
            room.setPhoto4(photos.size() > 3 ? photos.get(3) : null);
        }

        Room saved = roomRepository.save(room);
        return ResponseEntity.ok(new RoomDTO(saved));
    }

    @PreAuthorize("hasAuthority('admin')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> InactivateRoom(@PathVariable Integer id) {
        Optional<Room> roomOpt = roomRepository.findById(id);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Room room = roomOpt.get();
        room.setStatus(false);
        roomRepository.save(room);
        return ResponseEntity.ok("Room status updated to false successfully.");
    }

    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("/delete/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Integer id) {
        return InactivateRoom(id);
    }
}
