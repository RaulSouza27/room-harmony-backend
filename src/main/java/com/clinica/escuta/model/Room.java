package com.clinica.escuta.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "unit_id", nullable = false)
    private Integer unitId;

    @Column(nullable = false)
    private Boolean status = true;

    @Column(length = 255)
    private String description;

    @Column(length = 255)
    private String comments;

    @Column(columnDefinition = "TEXT")
    private String photo1;

    @Column(columnDefinition = "TEXT")
    private String photo2;

    @Column(columnDefinition = "TEXT")
    private String photo3;

    @Column(columnDefinition = "TEXT")
    private String photo4;
}
