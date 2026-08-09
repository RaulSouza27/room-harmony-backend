package com.salas.psi.harmony.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponseDTO {
    private String token;
    private String message;
    private Integer id;
    private String username;
    private String email;
    private String accessLevel;

    public LoginResponseDTO(String token, String message) {
        this.token = token;
        this.message = message;
    }

    public LoginResponseDTO(String token, String message, Integer id, String username, String email, String accessLevel) {
        this.token = token;
        this.message = message;
        this.id = id;
        this.username = username;
        this.email = email;
        this.accessLevel = accessLevel;
    }
}
