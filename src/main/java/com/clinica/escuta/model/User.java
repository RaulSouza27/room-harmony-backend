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
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private Boolean status = true;

    @Column(name = "access_level", nullable = false, length = 20)
    private String accessLevel;

    @Column(name = "profession_id")
    private Integer professionId;

    @Column(nullable = false)
    private Boolean FirstLogin = true;

    @Column(nullable = false)
    private Boolean mustCompleteTour = true;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(nullable = false, length = 50)
    private String address;

    @Column(nullable = false, length = 8)
    private String cep;

    @Column(columnDefinition = "TEXT")
    private String photo;

    @Column(name = "board_number", nullable = false, length = 11)
    private String boardNumber;
}
