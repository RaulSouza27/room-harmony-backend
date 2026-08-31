package com.clinica.escuta.DTO;

import com.clinica.escuta.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDTO {
    private Integer id;
    private String username;
    private String email;
    private String password;
    private String accessLevel;
    private Boolean status;
    private Integer professionId;
    private boolean isFirstLogin;
    private boolean mustCompleteTour;
    private String phone;
    private String cpf;
    private String address;
    private String cep;
    private String photo;
    private String boardNumber;

    public UserDTO() {
    }

    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.accessLevel = user.getAccessLevel();
        this.status = Boolean.TRUE.equals(user.getStatus());
        this.professionId = user.getProfessionId();
        this.isFirstLogin = Boolean.TRUE.equals(user.getFirstLogin());
        this.mustCompleteTour = Boolean.TRUE.equals(user.getMustCompleteTour());
        this.phone = user.getPhone();
        this.cpf = user.getCpf();
        this.address = user.getAddress();
        this.cep = user.getCep();
        this.photo = user.getPhoto();
        this.boardNumber = user.getBoardNumber();
    }
}
