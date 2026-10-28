package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.ReservationDTO;
import com.clinica.escuta.model.Reservation;
import com.clinica.escuta.model.Room;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.HolidayRepository;
import com.clinica.escuta.repository.ReservationRepository;
import com.clinica.escuta.repository.RoomRepository;
import com.clinica.escuta.repository.UnitRepository;
import com.clinica.escuta.repository.UserRepository;
import com.clinica.escuta.service.CreditService;
import com.clinica.escuta.service.TelegramNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Regressão de segurança: locatários NÃO podem criar/alterar reservas como aprovadas
 * nem mexer em reservas de terceiros. O status deve ser decidido pelo servidor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationsControllerSecurityTest {

    private static final int ROOM_ID = 10;
    private static final int UNIT_ID = 1;
    private static final int TENANT_ID = 42;
    private static final int OTHER_TENANT_ID = 99;
    // Segunda-feira, dentro do horário padrão (08:00–18:00), bem no futuro (regra de 24h).
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    @Mock private ReservationRepository reservationRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private TelegramNotificationService telegramNotificationService;
    @Mock private UserRepository userRepository;
    @Mock private CreditService creditService;

    private ReservationsController controller;

    @BeforeEach
    void setUp() {
        controller = new ReservationsController(reservationRepository, roomRepository, unitRepository,
                holidayRepository, telegramNotificationService, userRepository, creditService);

        Room room = new Room();
        room.setId(ROOM_ID);
        room.setUnitId(UNIT_ID);
        Unit unit = new Unit();
        unit.setId(UNIT_ID);

        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(unitRepository.findById(UNIT_ID)).thenReturn(Optional.of(unit));
        when(holidayRepository.findByStatusTrue()).thenReturn(Collections.emptyList());
        when(reservationRepository.findByStatusTrue()).thenReturn(Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            if (r.getId() == null) r.setId(1000);
            return r;
        });

        mockUser("tenant", TENANT_ID);
        mockUser("other", OTHER_TENANT_ID);
        mockUser("adminUser", 1);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- helpers ----------

    private void mockUser(String username, int id) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(u));
    }

    private void loginAs(String username, String accessLevel) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority(accessLevel))));
    }

    private ReservationDTO newRequest(String status, Integer userId) {
        ReservationDTO dto = new ReservationDTO();
        dto.setRoomsId(ROOM_ID);
        dto.setUserId(userId);
        dto.setData(MONDAY);
        dto.setHoraInicio(LocalTime.of(10, 0));
        dto.setHoraFim(LocalTime.of(11, 0));
        dto.setDepositImage("data:image/png;base64,AAAA");
        dto.setRecorrencia("unica");
        dto.setStatusString(status);
        return dto;
    }

    private Reservation existing(int ownerId, String status) {
        Reservation r = new Reservation();
        r.setId(500);
        r.setRoomsId(ROOM_ID);
        r.setUserId(ownerId);
        r.setData(MONDAY);
        r.setHoraInicio(LocalTime.of(10, 0));
        r.setHoraFim(LocalTime.of(11, 0));
        r.setDepositImage("img");
        r.setComments(status + "|||unica");
        r.setStatus(true);
        when(reservationRepository.findById(500)).thenReturn(Optional.of(r));
        return r;
    }

    private Reservation captureSaved() {
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static String statusOf(Reservation r) {
        return r.getComments().split("\\|", -1)[0];
    }

    private static String approverOf(Reservation r) {
        return r.getComments().split("\\|", -1)[2];
    }

    // ---------- POST /reservations ----------

    @Nested
    @DisplayName("Criação de reserva")
    class Create {

        @ParameterizedTest(name = "locatário enviando status=''{0}'' deve gerar reserva PENDENTE")
        @ValueSource(strings = {"aprovada", "pendente", "negada", "cancelada", "solicitacao_cancelamento"})
        void tenantReservationIsAlwaysPending(String forgedStatus) {
            loginAs("tenant", "locatario");
            ReservationDTO req = newRequest(forgedStatus, TENANT_ID);
            req.setAprovadoPor("hacker");
            req.setMotivoNegacao("forjado");

            ResponseEntity<?> resp = controller.createReservation(req);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            Reservation saved = captureSaved();
            assertEquals("pendente", statusOf(saved));
            assertEquals("", approverOf(saved), "locatário não pode definir aprovador");
            assertEquals("pendente", ((ReservationDTO) resp.getBody()).getStatusString());
        }

        @Test
        void tenantReservationWithoutStatusIsPending() {
            loginAs("tenant", "locatario");
            controller.createReservation(newRequest(null, TENANT_ID));
            assertEquals("pendente", statusOf(captureSaved()));
        }

        @Test
        void tenantCannotCreateReservationOnBehalfOfAnotherUser() {
            loginAs("tenant", "locatario");
            controller.createReservation(newRequest("pendente", OTHER_TENANT_ID));
            assertEquals(TENANT_ID, captureSaved().getUserId());
        }

        @Test
        void unknownAuthenticatedUserIsForbidden() {
            loginAs("ghost", "locatario");
            ResponseEntity<?> resp = controller.createReservation(newRequest("aprovada", TENANT_ID));
            assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void adminReservationIsApprovedForChosenUser() {
            loginAs("adminUser", "admin");
            controller.createReservation(newRequest(null, TENANT_ID));
            Reservation saved = captureSaved();
            assertEquals("aprovada", statusOf(saved));
            assertEquals(TENANT_ID, saved.getUserId());
            assertEquals("adminUser", approverOf(saved));
        }
    }

    // ---------- PUT /reservations/{id} ----------

    @Nested
    @DisplayName("Atualização de reserva")
    class Update {

        @Test
        void tenantCannotApproveOwnPendingReservation() {
            loginAs("tenant", "locatario");
            existing(TENANT_ID, "pendente");
            ReservationDTO patch = new ReservationDTO();
            patch.setStatusString("aprovada");

            ResponseEntity<?> resp = controller.updateReservation(500, patch);

            assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
            verify(reservationRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"aprovada", "negada", "pendente"})
        void tenantCannotSetAdminOnlyStatuses(String status) {
            loginAs("tenant", "locatario");
            existing(TENANT_ID, "pendente");
            ReservationDTO patch = new ReservationDTO();
            patch.setStatusString(status);

            assertEquals(HttpStatus.FORBIDDEN, controller.updateReservation(500, patch).getStatusCode());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void tenantCannotEditAnotherUsersReservation() {
            loginAs("tenant", "locatario");
            existing(OTHER_TENANT_ID, "pendente");
            ReservationDTO patch = new ReservationDTO();
            patch.setDescription("mexendo no que não é meu");

            assertEquals(HttpStatus.FORBIDDEN, controller.updateReservation(500, patch).getStatusCode());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void tenantCannotForgeApproverOrTransferOwnership() {
            loginAs("tenant", "locatario");
            existing(TENANT_ID, "pendente");
            ReservationDTO patch = new ReservationDTO();
            patch.setAprovadoPor("hacker");
            patch.setUserId(OTHER_TENANT_ID);

            controller.updateReservation(500, patch);

            Reservation saved = captureSaved();
            assertEquals("pendente", statusOf(saved));
            assertEquals("", approverOf(saved));
            assertEquals(TENANT_ID, saved.getUserId());
        }

        @Test
        void tenantCannotEditApprovedAvulsaReservation() {
            loginAs("tenant", "locatario");
            Reservation r = existing(TENANT_ID, "aprovada");
            r.setComments("aprovada||adminUser|unica");
            r.setPaidWithCredits(false);
            ReservationDTO patch = new ReservationDTO();
            patch.setHoraInicio(LocalTime.of(14, 0));
            patch.setHoraFim(LocalTime.of(15, 0));

            ResponseEntity<?> resp = controller.updateReservation(500, patch);
            assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        void tenantRescheduleOfHolidayReservationGoesBackToPending() {
            loginAs("tenant", "locatario");
            Reservation r = existing(TENANT_ID, "reagendar");
            r.setComments("reagendar|Feriado|adminUser|unica");
            r.setPaidWithCredits(false);
            ReservationDTO patch = new ReservationDTO();
            patch.setHoraInicio(LocalTime.of(14, 0));
            patch.setHoraFim(LocalTime.of(15, 0));

            controller.updateReservation(500, patch);

            Reservation saved = captureSaved();
            assertEquals("pendente", statusOf(saved));
            assertEquals("", approverOf(saved));
        }

        @Test
        void tenantCanCreateCancelAndEditCreditReservationWith24hAdvance() {
            loginAs("tenant", "locatario");
            ReservationDTO req = newRequest(null, TENANT_ID);
            req.setDepositImage(null);
            req.setPaidWithCredits(true);
            req.setHoraInicio(LocalTime.of(9, 0));
            req.setHoraFim(LocalTime.of(11, 0)); // 2 horas inteiras

            ResponseEntity<?> createResp = controller.createReservation(req);
            assertEquals(HttpStatus.OK, createResp.getStatusCode());
            Reservation created = captureSaved();
            assertEquals("aprovada", statusOf(created));
            assertTrue(created.getPaidWithCredits());
            assertEquals(2, created.getCreditHoursUsed());
            verify(creditService).consumeCreditsForReservation(any(), eq(UNIT_ID), any(), eq(2), any());

            // Cancelamento com >= 24h de antecedência devolve os créditos
            Reservation creditRes = existing(TENANT_ID, "aprovada");
            creditRes.setPaidWithCredits(true);
            creditRes.setCreditHoursUsed(2);

            ReservationDTO cancelPatch = new ReservationDTO();
            cancelPatch.setStatusString("cancelada");
            ResponseEntity<?> cancelResp = controller.updateReservation(500, cancelPatch);
            assertEquals(HttpStatus.OK, cancelResp.getStatusCode());
            verify(creditService).refundCreditsForReservation(eq(creditRes), any(), isNull());
        }

        @Test
        void tenantCannotCancelOrEditCreditReservationWithin24h() {
            loginAs("tenant", "locatario");
            Reservation creditRes = existing(TENANT_ID, "aprovada");
            creditRes.setPaidWithCredits(true);
            creditRes.setCreditHoursUsed(1);
            creditRes.setData(LocalDate.now());
            creditRes.setHoraInicio(LocalTime.now().plusHours(2));
            creditRes.setHoraFim(LocalTime.now().plusHours(3));

            ReservationDTO cancelPatch = new ReservationDTO();
            cancelPatch.setStatusString("cancelada");
            assertEquals(HttpStatus.BAD_REQUEST, controller.updateReservation(500, cancelPatch).getStatusCode());
        }

        @Test
        void tenantCanRequestCancellationOfOwnReservation() {
            loginAs("tenant", "locatario");
            existing(TENANT_ID, "aprovada");
            ReservationDTO patch = new ReservationDTO();
            patch.setStatusString("solicitacao_cancelamento");

            assertEquals(HttpStatus.OK, controller.updateReservation(500, patch).getStatusCode());
            assertEquals("solicitacao_cancelamento", statusOf(captureSaved()));
        }

        @Test
        void adminCanApproveReservation() {
            loginAs("adminUser", "admin");
            existing(TENANT_ID, "pendente");
            ReservationDTO patch = new ReservationDTO();
            patch.setStatusString("aprovada");
            patch.setAprovadoPor("adminUser");

            assertEquals(HttpStatus.OK, controller.updateReservation(500, patch).getStatusCode());
            Reservation saved = captureSaved();
            assertEquals("aprovada", statusOf(saved));
            assertEquals("adminUser", approverOf(saved));
        }
    }

    // ---------- DELETE ----------

    @Nested
    @DisplayName("Exclusão de reserva")
    class Delete {

        @Test
        void deleteEndpointsAreAdminOnly() throws NoSuchMethodException {
            PreAuthorize single = ReservationsController.class
                    .getMethod("deleteReservation", Integer.class).getAnnotation(PreAuthorize.class);
            PreAuthorize batch = ReservationsController.class
                    .getMethod("deleteReservationsBatch", List.class).getAnnotation(PreAuthorize.class);
            PreAuthorize cancelBatch = ReservationsController.class
                    .getMethod("cancelReservationsBatch", List.class).getAnnotation(PreAuthorize.class);

            assertNotNull(single, "DELETE /reservations/{id} precisa de @PreAuthorize");
            assertNotNull(batch, "POST /reservations/delete-batch precisa de @PreAuthorize");
            assertNotNull(cancelBatch, "POST /reservations/cancel-batch precisa de @PreAuthorize");
            assertEquals("hasAuthority('admin')", single.value());
            assertEquals("hasAuthority('admin')", batch.value());
            assertEquals("hasAuthority('admin')", cancelBatch.value());
        }

        @Test
        void cancelReservationsBatchMarksSelectedAsCancelledAndFreesSlot() {
            Reservation r1 = existing(TENANT_ID, "aprovada");
            r1.setId(501);
            r1.setComments("aprovada||adminUser|unica");
            r1.setStatus(true);

            Reservation r2 = existing(OTHER_TENANT_ID, "pendente");
            r2.setId(502);
            r2.setComments("pendente|||semanal_mensal");
            r2.setStatus(true);

            when(reservationRepository.findAllById(List.of(501, 502))).thenReturn(List.of(r1, r2));

            ResponseEntity<?> resp = controller.cancelReservationsBatch(List.of(501, 502));
            assertEquals(HttpStatus.OK, resp.getStatusCode());

            assertEquals("cancelada||adminUser|unica", r1.getComments());
            assertFalse(r1.getStatus());
            assertEquals("cancelada|||semanal_mensal", r2.getComments());
            assertFalse(r2.getStatus());
            verify(reservationRepository).saveAll(List.of(r1, r2));
        }
    }
}
