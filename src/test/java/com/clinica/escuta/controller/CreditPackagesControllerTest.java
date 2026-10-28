package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.CreditPackageDTO;
import com.clinica.escuta.model.CreditPackage;
import com.clinica.escuta.model.CreditTransaction;
import com.clinica.escuta.model.Reservation;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.CreditPackageRepository;
import com.clinica.escuta.repository.CreditTransactionRepository;
import com.clinica.escuta.repository.UnitRepository;
import com.clinica.escuta.repository.UserRepository;
import com.clinica.escuta.service.CreditService;
import com.clinica.escuta.service.TelegramNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditPackagesControllerTest {

    @Mock private CreditPackageRepository creditPackageRepository;
    @Mock private CreditTransactionRepository creditTransactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private TelegramNotificationService telegramNotificationService;

    private CreditService creditService;
    private CreditPackagesController controller;

    private User tenant;
    private User admin;
    private Unit unit;

    @BeforeEach
    void setUp() {
        creditService = new CreditService(creditPackageRepository, creditTransactionRepository);
        controller = new CreditPackagesController(
                creditService,
                creditPackageRepository,
                creditTransactionRepository,
                userRepository,
                unitRepository,
                telegramNotificationService
        );

        tenant = new User();
        tenant.setId(42);
        tenant.setUsername("Dr. Locatário");
        tenant.setEmail("locatario@clinica.com");
        tenant.setAccessLevel("locatario");

        admin = new User();
        admin.setId(1);
        admin.setUsername("Admin");
        admin.setEmail("admin@clinica.com");
        admin.setAccessLevel("admin");

        unit = new Unit();
        unit.setId(10);
        unit.setName("Unidade Centro");
        unit.setStatus(true);

        when(userRepository.findByUsername("locatario")).thenReturn(Optional.of(tenant));
        when(userRepository.findById(42)).thenReturn(Optional.of(tenant));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(1)).thenReturn(Optional.of(admin));
        when(unitRepository.findById(10)).thenReturn(Optional.of(unit));

        when(creditPackageRepository.save(any(CreditPackage.class))).thenAnswer(inv -> {
            CreditPackage p = inv.getArgument(0);
            if (p.getId() == null) p.setId(100);
            return p;
        });
        when(creditTransactionRepository.save(any(CreditTransaction.class))).thenAnswer(inv -> {
            CreditTransaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(200);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role)))
        );
    }

    @Test
    @DisplayName("Locatário solicita compra de pacote com comprovante e unidade ativa")
    void tenantCanCreatePackagePurchaseRequest() {
        loginAs("locatario", "locatario");

        CreditPackageDTO req = new CreditPackageDTO();
        req.setUnitId(10);
        req.setReceiptImage("data:image/png;base64,COMPROVANTE");

        ResponseEntity<?> resp = controller.createPackageRequest(req);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());

        CreditPackageDTO body = (CreditPackageDTO) resp.getBody();
        assertNotNull(body);
        assertEquals(12, body.getTotalCredits());
        assertEquals(0, body.getRemainingCredits());
        assertEquals("PENDING", body.getStatus());
        verify(telegramNotificationService).notifyNewCreditPackage(any());
    }

    @Test
    @DisplayName("Admin aprova pacote pendente, liberando 12 créditos válidos por 30 dias")
    void adminApprovalGrants12CreditsAnd30DaysValidity() {
        loginAs("admin", "admin");

        CreditPackage pending = new CreditPackage();
        pending.setId(100);
        pending.setUserId(42);
        pending.setUnitId(10);
        pending.setTotalCredits(12);
        pending.setRemainingCredits(0);
        pending.setStatus("PENDING");
        when(creditPackageRepository.findById(100)).thenReturn(Optional.of(pending));

        ResponseEntity<?> resp = controller.approvePackage(100);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        CreditPackageDTO dto = (CreditPackageDTO) resp.getBody();
        assertNotNull(dto);
        assertEquals("APPROVED", dto.getStatus());
        assertEquals(12, dto.getRemainingCredits());
        assertEquals(LocalDate.now().plusDays(30), dto.getExpiresAt().toLocalDate());

        ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionRepository).save(txCaptor.capture());
        assertEquals("CREDITO_PACOTE", txCaptor.getValue().getType());
        assertEquals(12, txCaptor.getValue().getAmount());
        assertEquals(12, txCaptor.getValue().getBalanceAfter());
    }

    @Test
    @DisplayName("Consumo de créditos segue FEFO (consome primeiro o pacote que vence antes)")
    void consumeCreditsFollowsFEFOAndValidatesExpirationAgainstReservationDate() {
        CreditPackage olderPkg = new CreditPackage();
        olderPkg.setId(1);
        olderPkg.setUserId(42);
        olderPkg.setUnitId(10);
        olderPkg.setTotalCredits(12);
        olderPkg.setRemainingCredits(1);
        olderPkg.setStatus("APPROVED");
        olderPkg.setExpiresAt(LocalDate.now().plusDays(10).atTime(23, 59, 59));

        CreditPackage newerPkg = new CreditPackage();
        newerPkg.setId(2);
        newerPkg.setUserId(42);
        newerPkg.setUnitId(10);
        newerPkg.setTotalCredits(12);
        newerPkg.setRemainingCredits(12);
        newerPkg.setStatus("APPROVED");
        newerPkg.setExpiresAt(LocalDate.now().plusDays(25).atTime(23, 59, 59));

        when(creditPackageRepository.findActivePackagesForUpdate(42, 10))
                .thenReturn(List.of(olderPkg, newerPkg));

        Reservation res = new Reservation();
        res.setId(999);
        res.setUserId(42);
        res.setRoomsId(5);
        res.setData(LocalDate.now().plusDays(5));
        res.setHoraInicio(LocalTime.of(9, 0));
        res.setHoraFim(LocalTime.of(11, 0));

        creditService.consumeCreditsForReservation(res, 10, "Sala 1", 2, tenant);

        assertEquals(0, olderPkg.getRemainingCredits(), "Pacote que vence antes deve ser zerado primeiro");
        assertEquals(11, newerPkg.getRemainingCredits(), "Pacote seguinte cobre a hora restante");
    }

    @Test
    @DisplayName("Impede reserva com crédito em data posterior ao vencimento do pacote")
    void preventsReservationAfterPackageExpirationDate() {
        CreditPackage pkg = new CreditPackage();
        pkg.setId(1);
        pkg.setUserId(42);
        pkg.setUnitId(10);
        pkg.setTotalCredits(12);
        pkg.setRemainingCredits(12);
        pkg.setStatus("APPROVED");
        pkg.setExpiresAt(LocalDate.now().plusDays(10).atTime(23, 59, 59));

        when(creditPackageRepository.findActivePackagesForUpdate(42, 10))
                .thenReturn(List.of(pkg));

        Reservation resAfterExpiry = new Reservation();
        resAfterExpiry.setId(1000);
        resAfterExpiry.setUserId(42);
        resAfterExpiry.setRoomsId(5);
        resAfterExpiry.setData(LocalDate.now().plusDays(15)); // 5 dias após o vencimento!
        resAfterExpiry.setHoraInicio(LocalTime.of(9, 0));
        resAfterExpiry.setHoraFim(LocalTime.of(10, 0));

        assertThrows(IllegalArgumentException.class, () ->
                creditService.consumeCreditsForReservation(resAfterExpiry, 10, "Sala 1", 1, tenant)
        );
        assertEquals(12, pkg.getRemainingCredits());
    }

    @Test
    @DisplayName("Endpoints administrativos exigem autoridade admin")
    void adminEndpointsHavePreAuthorizeAnnotation() throws NoSuchMethodException {
        PreAuthorize approveAuth = CreditPackagesController.class
                .getMethod("approvePackage", Integer.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize rejectAuth = CreditPackagesController.class
                .getMethod("rejectPackage", Integer.class, CreditPackageDTO.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(approveAuth);
        assertNotNull(rejectAuth);
        assertEquals("hasAuthority('admin')", approveAuth.value());
        assertEquals("hasAuthority('admin')", rejectAuth.value());
    }
}

