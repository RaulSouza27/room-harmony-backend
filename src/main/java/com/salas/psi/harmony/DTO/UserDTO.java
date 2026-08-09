package com.salas.psi.harmony.DTO;

import com.salas.psi.harmony.model.User;
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
    private String phone;
    private String specialty;
    private String photo;
    private List<String> units;

    public UserDTO() {
    }

    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.accessLevel = user.getAccessLevel();
        this.status = Boolean.TRUE.equals(user.getStatus());
        this.phone = "";
        this.specialty = "";
        this.photo = "";
        this.units = java.util.Collections.emptyList();
    }
}
