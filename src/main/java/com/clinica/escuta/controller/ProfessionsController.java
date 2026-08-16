package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.ProfissionsDTO;
import com.clinica.escuta.model.Profissions;
import com.clinica.escuta.repository.ProfissionsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/profissions")
public class ProfessionsController {

    private final ProfissionsRepository professionsRepository;

    public ProfessionsController(ProfissionsRepository professionsRepository) {
        this.professionsRepository = professionsRepository;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody ProfissionsDTO request) {
        if (request.getId() != null || request.getProfission() != null) {
            return ResponseEntity.badRequest().body("Missing information to create Profissions");
        }
        Profissions entity = ProfissionsDTO.toEntity(request);
        Profissions saved = professionsRepository.save(entity);
        return ResponseEntity.ok().body(saved);
    }

    @GetMapping("readAll")
    public ResponseEntity<?> readAll() {
        List<Profissions> list = professionsRepository.findByidOrderByNameAsc();
        return ResponseEntity.ok().body(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Profissions> findById(@PathVariable Integer id) {
        Optional<Profissions> entity = professionsRepository.findById(id);
        return entity.map(profissions -> ResponseEntity.ok().body(profissions)).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("{id")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody ProfissionsDTO request) {
        Optional<Profissions> entity = professionsRepository.findById(id);
        if (entity.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        entity.get().setProfission(request.getProfission());
        professionsRepository.save(entity.get());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestBody ProfissionsDTO request) {
        if (request.getId() != null) {
            return ResponseEntity.badRequest().body("Missing information to delete Profissions");
        }
        Profissions entity = ProfissionsDTO.toEntity(request);
        professionsRepository.delete(entity);
        return ResponseEntity.ok().build();
    }

}
