package com.clinica.escuta.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "rooms_id", nullable = false)
    private Integer roomsId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(name = "deposit_image", nullable = false, columnDefinition = "TEXT")
    private String depositImage = "empty";

    @Column(length = 255)
    private String description;

    @Column(length = 255)
    private String comments;

    @Column(nullable = false)
    private Boolean status = true;

    public Reservation() {
    }

    public Reservation(
            Integer id,
            Integer roomsId,
            Integer userId,
            LocalDate data,
            LocalTime horaInicio,
            LocalTime horaFim,
            String depositImage,
            String description,
            String comments,
            Boolean status
    ) {
        this.id = id;
        this.roomsId = roomsId;
        this.userId = userId;
        this.data = data;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.depositImage = depositImage;
        this.description = description;
        this.comments = comments;
        this.status = status;
    }
}
