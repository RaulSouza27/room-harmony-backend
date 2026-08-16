package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.UnitDTO;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.repository.UnitRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/units")
public class UnitsController {

    private final UnitRepository unitRepository;

    public UnitsController(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    @PreAuthorize("hasAuthority('admin')")
    @PostMapping
    public ResponseEntity<?> createUnit(@RequestBody UnitDTO request) {
        if (request.getName() == null || request.getAddress() == null ) {
            return ResponseEntity.badRequest().body("Missing required fields (name, address).");
        }

        if (unitRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("Unit name already exists.");
        }

        Unit unit = new Unit();
        unit.setName(request.getName());
        unit.setAddress(request.getAddress());
        unit.setStatus(request.isStatus());

        Unit savedUnit = unitRepository.save(unit);
        return ResponseEntity.ok(savedUnit);
    }


    public List<Unit> getAllActiveUnits() {
        return unitRepository.findByStatusTrue();
    }
    public List<Unit> getAllInactiveUnits() {
        return unitRepository.findByStatusFalse();
    }

    @GetMapping("/readAll")
    public ResponseEntity<List<UnitDTO>> readAll() {
        List<UnitDTO> units = new ArrayList<>();
        for (Unit unit : getAllActiveUnits()) {
            units.add(new UnitDTO(unit));
        }

        for (Unit unit : getAllInactiveUnits()) {
            units.add(new UnitDTO(unit));
        }

        return ResponseEntity.ok(units);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUnitById(@PathVariable Integer id) {
        return unitRepository.findById(id)
                .map(user -> ResponseEntity.ok(new UnitDTO(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('admin')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUnit(@PathVariable Integer id, @RequestBody UnitDTO request) {
        Optional<Unit> unitOpt = unitRepository.findById(id);
        if (unitOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Unit unit = unitOpt.get();

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            Optional<Unit> existing = unitRepository.findByName(request.getName());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("Unit already exists.");
            }
            unit.setName(request.getName());
        }
        if (request.getAddress() != null && !request.getAddress().trim().isEmpty()) {
            unit.setAddress(request.getAddress());
        }
        unit.setStatus(request.isStatus());

        Unit updatedUser = unitRepository.save(unit);
        return ResponseEntity.ok(new UnitDTO(updatedUser));
    }

    @PreAuthorize("hasAuthority('admin')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> InactivateUnit(@PathVariable Integer id) {
        Optional<Unit> UnitOpt = unitRepository.findById(id);
        if (UnitOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Unit unit = UnitOpt.get();
        unit.setStatus(false);
        unitRepository.save(unit);
        return ResponseEntity.ok("Unit status updated to false successfully.");
    }

    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("/delete/{id}")
    public ResponseEntity<?> deleteUnit(@PathVariable Integer id) {
        return InactivateUnit(id);
    }
}