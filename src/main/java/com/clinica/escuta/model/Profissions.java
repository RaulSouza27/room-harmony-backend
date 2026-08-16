package com.clinica.escuta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "profissions")
public class Profissions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "profission", nullable = false, columnDefinition = "TEXT")
    private String profission = "empty";
}