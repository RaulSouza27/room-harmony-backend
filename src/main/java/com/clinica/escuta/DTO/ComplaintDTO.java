package com.clinica.escuta.DTO;

import com.clinica.escuta.model.Complaint;
import com.clinica.escuta.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintDTO {
    private Integer id;
    private Integer userId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private Boolean anonymous;
    private String complaints;
    private Boolean solved;
    private String resolutionNotes;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public ComplaintDTO(Complaint entity, User author) {
        this.id = entity.getId();
        this.userId = entity.getUserId();
        this.anonymous = entity.getUserId() == null;
        if (entity.getUserId() != null && author != null) {
            this.userName = author.getUsername();
            this.userEmail = author.getEmail();
            this.userPhone = author.getPhone();
        } else if (entity.getUserId() != null) {
            this.userName = "Profissional #" + entity.getUserId();
            this.userEmail = null;
            this.userPhone = null;
        } else {
            this.userName = "Anônimo";
            this.userEmail = null;
            this.userPhone = null;
        }
        this.complaints = entity.getComplaints();
        this.solved = entity.getSolved();
        this.resolutionNotes = entity.getResolutionNotes();
        this.resolvedBy = entity.getResolvedBy();
        this.resolvedAt = entity.getResolvedAt();
        this.createdAt = entity.getCreatedAt();
    }
}

