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

    public Room() {
    }

    public Room(
            Integer id,
            String name,
            Integer unitId,
            Boolean status,
            String description,
            String comments,
            String photo1,
            String photo2,
            String photo3,
            String photo4
    ) {
        this.id = id;
        this.name = name;
        this.unitId = unitId;
        this.status = status;
        this.description = description;
        this.comments = comments;
        this.photo1 = photo1;
        this.photo2 = photo2;
        this.photo3 = photo3;
        this.photo4 = photo4;
    }
}
