package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.UnitDTO;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.repository.UnitRepository;
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
class UnitsControllerTest {

    @Mock
    private UnitRepository unitRepository;

    private UnitsController unitsController;

    @BeforeEach
    void setUp() {
        unitsController = new UnitsController(unitRepository);
    }

    @Test
    void shouldCreateUnitSuccessfully() {
        UnitDTO dto = new UnitDTO();
        dto.setName("Unidade Jardins");
        dto.setAddress("Rua Oscar Freire, 1000");
        dto.setStatus(true);

        when(unitRepository.findByName("Unidade Jardins")).thenReturn(Optional.empty());
        when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> {
            Unit saved = invocation.getArgument(0);
            saved.setId(1);
            return saved;
        });

        ResponseEntity<?> response = unitsController.createUnit(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof UnitDTO);
        UnitDTO result = (UnitDTO) response.getBody();
        assertEquals("Unidade Jardins", result.getName());
    }

    @Test
    void shouldReturnBadRequestWhenNameMissing() {
        UnitDTO dto = new UnitDTO();
        dto.setAddress("Rua Oscar Freire, 1000");

        ResponseEntity<?> response = unitsController.createUnit(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required fields (name, address).", response.getBody());
    }

    @Test
    void shouldReturnBadRequestWhenNameAlreadyExists() {
        UnitDTO dto = new UnitDTO();
        dto.setName("Unidade Jardins");
        dto.setAddress("Rua Oscar Freire, 1000");

        when(unitRepository.findByName("Unidade Jardins")).thenReturn(Optional.of(new Unit()));

        ResponseEntity<?> response = unitsController.createUnit(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Unit name already exists.", response.getBody());
    }

    @Test
    void shouldReadAllUnits() {
        Unit activeUnit = new Unit();
        activeUnit.setId(1);
        activeUnit.setName("Unidade 1");
        activeUnit.setAddress("Endereço 1");
        activeUnit.setStatus(true);

        Unit inactiveUnit = new Unit();
        inactiveUnit.setId(2);
        inactiveUnit.setName("Unidade 2");
        inactiveUnit.setAddress("Endereço 2");
        inactiveUnit.setStatus(false);

        when(unitRepository.findByStatusTrue()).thenReturn(List.of(activeUnit));
        when(unitRepository.findByStatusFalse()).thenReturn(List.of(inactiveUnit));

        ResponseEntity<List<UnitDTO>> response = unitsController.readAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void shouldInactivateUnit() {
        Unit unit = new Unit();
        unit.setId(1);
        unit.setStatus(true);

        when(unitRepository.findById(1)).thenReturn(Optional.of(unit));

        ResponseEntity<?> response = unitsController.InactivateUnit(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(unit.getStatus());
        verify(unitRepository, times(1)).save(unit);
    }
}
