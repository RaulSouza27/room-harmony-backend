package com.clinica.escuta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "credit_packages")
public class CreditPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "unit_id", nullable = false)
    private Integer unitId;

    @Column(name = "total_credits", nullable = false)
    private Integer totalCredits = 12;

    @Column(name = "remaining_credits", nullable = false)
    private Integer remainingCredits = 0;

    @Column(name = "receipt_image", nullable = false, columnDefinition = "TEXT")
    private String receiptImage;

    /**
     * Status do pacote: "PENDING", "APPROVED", "REJECTED", "EXPIRED"
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private Integer reviewedByUserId;

    @Column(name = "reviewed_by_name", length = 150)
    private String reviewedByName;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}

