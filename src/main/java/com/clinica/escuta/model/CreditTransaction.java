package com.clinica.escuta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "package_id", nullable = false)
    private Integer packageId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "unit_id", nullable = false)
    private Integer unitId;

    @Column(name = "reservation_id")
    private Integer reservationId;

    /**
     * Tipos: "CREDITO_PACOTE", "USO_RESERVA", "ESTORNO_CANCELAMENTO", "AJUSTE_EDICAO", "EXPIRACAO"
     */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    /**
     * Quantidade de créditos movimentada (positiva para entrada/estorno, negativa para consumo/expiração).
     */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /**
     * Saldo restante no pacote de origem imediatamente após esta movimentação.
     */
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "performed_by_user_id")
    private Integer performedByUserId;

    @Column(name = "performed_by_name", length = 150)
    private String performedByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

