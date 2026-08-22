package com.clinica.escuta.DTO;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LoginResponseDTO {
    private String token;
    private String message;
    private Integer id;
    private String username;
    private String email;
    private String accessLevel;
    private boolean status;
    private boolean firstLogin;
    private boolean mustCompleteTour;
}
