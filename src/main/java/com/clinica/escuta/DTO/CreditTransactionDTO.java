package com.clinica.escuta.DTO;

import com.clinica.escuta.model.CreditTransaction;
import com.clinica.escuta.model.Unit;
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
public class CreditTransactionDTO {
    private Integer id;
    private Integer packageId;
    private Integer userId;
    private String userName;
    private String userEmail;
    private Integer unitId;
    private String unitName;
    private Integer reservationId;
    private String type;
    private Integer amount;
    private Integer balanceAfter;
    private String description;
    private Integer performedByUserId;
    private String performedByName;
    private LocalDateTime createdAt;

    public CreditTransactionDTO(CreditTransaction entity, User user, Unit unit) {
        this.id = entity.getId();
        this.packageId = entity.getPackageId();
        this.userId = entity.getUserId();
        if (user != null) {
            this.userName = user.getUsername();
            this.userEmail = user.getEmail();
        } else {
            this.userName = "Profissional #" + entity.getUserId();
        }
        this.unitId = entity.getUnitId();
        this.unitName = unit != null ? unit.getName() : ("Unidade #" + entity.getUnitId());
        this.reservationId = entity.getReservationId();
        this.type = entity.getType();
        this.amount = entity.getAmount();
        this.balanceAfter = entity.getBalanceAfter();
        this.description = entity.getDescription();
        this.performedByUserId = entity.getPerformedByUserId();
        this.performedByName = entity.getPerformedByName();
        this.createdAt = entity.getCreatedAt();
    }
}

