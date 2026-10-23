package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.ProfissionsDTO;
import com.clinica.escuta.annotation.Auditable;
import com.clinica.escuta.config.DatabaseSequenceSynchronizer;
import com.clinica.escuta.model.Profissions;
import com.clinica.escuta.repository.ProfissionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/profissions")
public class ProfessionsController {

    private final ProfissionsRepository professionsRepository;
    private final DatabaseSequenceSynchronizer sequenceSynchronizer;

    public ProfessionsController(ProfissionsRepository professionsRepository) {
        this(professionsRepository, null);
    }

    @Autowired
    public ProfessionsController(
            ProfissionsRepository professionsRepository,
            DatabaseSequenceSynchronizer sequenceSynchronizer
    ) {
        this.professionsRepository = professionsRepository;
        this.sequenceSynchronizer = sequenceSynchronizer;
    }

    @Auditable(action = "CRIACAO_PROFISSAO", targetType = "PROFISSAO")
    @PreAuthorize("hasAuthority('admin')")
    @PostMapping
    public ResponseEntity<?> save(@RequestBody ProfissionsDTO request) {
        if (request.getId() != null || request.getProfission() == null || request.getProfission().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing information to create Profissions");
        }
        try {
            Profissions entity = ProfissionsDTO.toEntity(request);
            entity.setId(null);
            entity.setProfission(request.getProfission().trim());
            Profissions saved = professionsRepository.save(entity);
            return ResponseEntity.ok().body(saved);
        } catch (DataIntegrityViolationException ex) {
            if (sequenceSynchronizer != null) {
                sequenceSynchronizer.syncSequence("professions");
            }
            Profissions retryEntity = new Profissions();
            retryEntity.setProfission(request.getProfission().trim());
            Profissions saved = professionsRepository.save(retryEntity);
            return ResponseEntity.ok().body(saved);
        }
    }

    @GetMapping("readAll")
    public ResponseEntity<?> readAll() {
        List<Profissions> list = professionsRepository.findAllByOrderByProfissionAsc();
        return ResponseEntity.ok().body(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Profissions> findById(@PathVariable Integer id) {
        Optional<Profissions> entity = professionsRepository.findById(id);
        return entity.map(profissions -> ResponseEntity.ok().body(profissions)).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Auditable(action = "ALTERACAO_PROFISSAO", targetType = "PROFISSAO")
    @PreAuthorize("hasAuthority('admin')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody ProfissionsDTO request) {
        Optional<Profissions> entity = professionsRepository.findById(id);
        if (entity.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        entity.get().setProfission(request.getProfission() != null ? request.getProfission().trim() : "");
        Profissions saved = professionsRepository.save(entity.get());
        return ResponseEntity.ok(saved);
    }

    @Auditable(action = "EXCLUSAO_PROFISSAO", targetType = "PROFISSAO")
    @PreAuthorize("hasAuthority('admin')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        Optional<Profissions> entity = professionsRepository.findById(id);
        if (entity.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Profissions target = entity.get();
        professionsRepository.delete(target);
        return ResponseEntity.ok(target);
    }

}
