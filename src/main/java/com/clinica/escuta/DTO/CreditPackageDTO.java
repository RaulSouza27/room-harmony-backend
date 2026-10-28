package com.clinica.escuta.DTO;

import com.clinica.escuta.model.CreditPackage;
import com.clinica.escuta.model.Unit;
import com.clinica.escuta.model.User;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditPackageDTO {
  private Integer id;
  private Integer userId;
  private String userName;
  private String userEmail;
  private String userPhone;
  private Integer unitId;
  private String unitName;
  private Integer totalCredits;
  private Integer remainingCredits;
  private String receiptImage;
  private String status;
  private String rejectionReason;
  private LocalDateTime createdAt;
  private LocalDateTime reviewedAt;
  private Integer reviewedByUserId;
  private String reviewedByName;
  private LocalDateTime expiresAt;

  public CreditPackageDTO(CreditPackage entity, User user, Unit unit,
                          boolean includeReceipt) {
    this.id = entity.getId();
    this.userId = entity.getUserId();
    if (user != null) {
      this.userName = user.getUsername();
      this.userEmail = user.getEmail();
      this.userPhone = user.getPhone();
    } else {
      this.userName = "Profissional #" + entity.getUserId();
    }
    this.unitId = entity.getUnitId();
    this.unitName =
        unit != null ? unit.getName() : ("Unidade #" + entity.getUnitId());
    this.totalCredits = entity.getTotalCredits();
    this.remainingCredits = entity.getRemainingCredits();
    if (includeReceipt) {
      this.receiptImage = entity.getReceiptImage();
    } else {
      this.receiptImage =
          (entity.getReceiptImage() != null &&
           !entity.getReceiptImage().isBlank() &&
           !"empty".equalsIgnoreCase(entity.getReceiptImage()))
              ? "has_receipt"
              : "empty";
    }
    this.status = entity.getStatus();
    this.rejectionReason = entity.getRejectionReason();
    this.createdAt = entity.getCreatedAt();
    this.reviewedAt = entity.getReviewedAt();
    this.reviewedByUserId = entity.getReviewedByUserId();
    this.reviewedByName = entity.getReviewedByName();
    this.expiresAt = entity.getExpiresAt();
  }
}
