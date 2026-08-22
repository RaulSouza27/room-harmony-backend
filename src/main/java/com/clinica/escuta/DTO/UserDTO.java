package com.clinica.escuta.DTO;

import com.clinica.escuta.model.User;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class UserDTO {
    private Integer id;
    private String username;
    private String email;
    private String password;
    private String accessLevel;
    private boolean status;
    private Integer professionId;
    private boolean isFirstLogin;
    private boolean mustCompleteTour;

    public UserDTO() {
    }

    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.accessLevel = user.getAccessLevel();
        this.status = Boolean.TRUE.equals(user.getStatus());
        this.professionId = user.getProfessionId();
        this.isFirstLogin = user.getFirstLogin();
        this.mustCompleteTour = user.getMustCompleteTour();
    }
}
