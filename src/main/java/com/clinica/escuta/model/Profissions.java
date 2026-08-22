package com.clinica.escuta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "professions")
public class Profissions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "profession", nullable = false, columnDefinition = "TEXT")
    private String profission = "empty";
}