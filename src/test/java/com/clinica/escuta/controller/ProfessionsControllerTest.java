package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.ProfissionsDTO;
import com.clinica.escuta.model.Profissions;
import com.clinica.escuta.repository.ProfissionsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfessionsControllerTest {

    @Mock
    private ProfissionsRepository professionsRepository;

    private ProfessionsController professionsController;

    @BeforeEach
    void setUp() {
        professionsController = new ProfessionsController(professionsRepository);
    }

    @Test
    void shouldCreateProfessionSuccessfully() {
        ProfissionsDTO dto = new ProfissionsDTO();
        dto.setProfission("Psicologia");

        when(professionsRepository.save(any(Profissions.class))).thenAnswer(inv -> {
            Profissions p = inv.getArgument(0);
            p.setId(1);
            return p;
        });

        ResponseEntity<?> response = professionsController.save(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Profissions);
        Profissions saved = (Profissions) response.getBody();
        assertEquals("Psicologia", saved.getProfission());
    }

    @Test
    void shouldReturnBadRequestWhenProfissionNameMissing() {
        ProfissionsDTO dto = new ProfissionsDTO();

        ResponseEntity<?> response = professionsController.save(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReadAllProfessions() {
        Profissions p1 = new Profissions();
        p1.setId(1);
        p1.setProfission("Fonoaudiologia");

        Profissions p2 = new Profissions();
        p2.setId(2);
        p2.setProfission("Psicologia");

        when(professionsRepository.findAllByOrderByProfissionAsc()).thenReturn(List.of(p1, p2));

        ResponseEntity<?> response = professionsController.readAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
        List<?> list = (List<?>) response.getBody();
        assertEquals(2, list.size());
    }

    @Test
    void shouldDeleteProfession() {
        Profissions p = new Profissions();
        p.setId(1);
        p.setProfission("Psicologia");

        when(professionsRepository.findById(1)).thenReturn(Optional.of(p));

        ResponseEntity<?> response = professionsController.delete(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(professionsRepository, times(1)).delete(p);
    }
}
