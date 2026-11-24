package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.ComplaintDTO;
import com.clinica.escuta.config.DatabaseSequenceSynchronizer;
import com.clinica.escuta.model.Complaint;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.ComplaintRepository;
import com.clinica.escuta.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplaintsControllerTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DatabaseSequenceSynchronizer sequenceSynchronizer;

    private ComplaintsController controller;

    @BeforeEach
    void setUp() {
        controller = new ComplaintsController(complaintRepository, userRepository, sequenceSynchronizer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, String role, int userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
        User u = new User();
        u.setId(userId);
        u.setUsername(username);
        u.setEmail(username + "@teste.com");
        lenient().when(userRepository.findByUsername(username)).thenReturn(Optional.of(u));
    }

    @Test
    void tenantCanCreateIdentifiedComplaint() {
        loginAs("locador1", "locatario", 10);
        when(complaintRepository.countBySenderDailyHash(anyString())).thenReturn(0L);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintDTO dto = new ComplaintDTO();
        dto.setComplaints("Ar-condicionado da Sala 2 precisa de manutenção.");
        dto.setAnonymous(false);

        ResponseEntity<?> resp = controller.createComplaint(dto);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        ArgumentCaptor<Complaint> captor = ArgumentCaptor.forClass(Complaint.class);
        verify(complaintRepository).save(captor.capture());
        assertEquals(10, captor.getValue().getUserId());
        assertFalse(captor.getValue().getSolved());
    }

    @Test
    void tenantCanCreateAnonymousComplaintWithNullUserId() {
        loginAs("locador1", "locatario", 10);
        when(complaintRepository.countBySenderDailyHash(anyString())).thenReturn(1L);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintDTO dto = new ComplaintDTO();
        dto.setComplaints("Relato anônimo sobre limpeza.");
        dto.setAnonymous(true);

        ResponseEntity<?> resp = controller.createComplaint(dto);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        ArgumentCaptor<Complaint> captor = ArgumentCaptor.forClass(Complaint.class);
        verify(complaintRepository).save(captor.capture());
        assertNull(captor.getValue().getUserId(), "Reclamação anônima deve salvar user_id como NULL");
        assertNotNull(captor.getValue().getSenderDailyHash());
    }

    @Test
    void blocksFourthComplaintOnSameDay() {
        loginAs("locador1", "locatario", 10);
        when(complaintRepository.countBySenderDailyHash(anyString())).thenReturn(3L);

        ComplaintDTO dto = new ComplaintDTO();
        dto.setComplaints("Quarta tentativa no mesmo dia.");
        dto.setAnonymous(true);

        ResponseEntity<?> resp = controller.createComplaint(dto);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.getStatusCode());
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void adminCannotCreateComplaint() {
        loginAs("adminUser", "admin", 1);

        ComplaintDTO dto = new ComplaintDTO();
        dto.setComplaints("Teste admin");

        ResponseEntity<?> resp = controller.createComplaint(dto);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void adminCannotMarkAsSolvedWithoutResolutionNotes() {
        loginAs("adminUser", "admin", 1);
        Complaint existing = new Complaint();
        existing.setId(7);
        existing.setUserId(null);
        existing.setComplaints("Sugestão de melhoria");
        existing.setSolved(false);

        when(complaintRepository.findById(7)).thenReturn(Optional.of(existing));

        ComplaintDTO req = new ComplaintDTO();
        req.setSolved(true);

        ResponseEntity<?> resp = controller.updateSolvedStatus(7, req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(existing.getSolved());
        verify(complaintRepository, never()).save(any());
    }

    @Test
    void adminCanToggleSolvedStatusWithResolutionNotes() {
        loginAs("adminUser", "admin", 1);
        Complaint existing = new Complaint();
        existing.setId(7);
        existing.setUserId(null);
        existing.setComplaints("Sugestão de melhoria");
        existing.setSolved(false);

        when(complaintRepository.findById(7)).thenReturn(Optional.of(existing));
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintDTO req = new ComplaintDTO();
        req.setSolved(true);
        req.setResolutionNotes("Trocamos o equipamento e testamos com a equipe.");

        ResponseEntity<?> resp = controller.updateSolvedStatus(7, req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(existing.getSolved());
        assertEquals("Trocamos o equipamento e testamos com a equipe.", existing.getResolutionNotes());
        assertNotNull(existing.getResolvedBy());
        assertNotNull(existing.getResolvedAt());
    }

    @Test
    void tenantCanReadOwnIdentifiedComplaintsWithResolutionFeedback() {
        loginAs("locador1", "locatario", 10);
        Complaint c = new Complaint();
        c.setId(12);
        c.setUserId(10);
        c.setComplaints("Lâmpada da Sala 3 piscando.");
        c.setSolved(true);
        c.setResolutionNotes("Lâmpada substituída pela manutenção.");
        c.setResolvedBy("Admin (admin@clinica.com)");

        when(complaintRepository.findByUserIdOrderByCreatedAtDesc(10)).thenReturn(List.of(c));

        ResponseEntity<?> resp = controller.readMyIdentifiedComplaints();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<?> list = (List<?>) resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());
        ComplaintDTO dto = (ComplaintDTO) list.get(0);
        assertEquals("Lâmpada substituída pela manutenção.", dto.getResolutionNotes());
        assertTrue(dto.getSolved());
    }
}

