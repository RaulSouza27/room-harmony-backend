package com.clinica.escuta.DTO;

import com.clinica.escuta.model.Reservation;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class ReservationDTO {
    private Integer id;
    private Integer roomsId;
    private Integer userId;
    private LocalDate data;
    private LocalTime horaInicio;
    private LocalTime horaFim;
    private String depositImage;
    private String description;
    private String statusString; // "pendente", "aprovada", "negada", "cancelada"
    private String motivoNegacao;
    private String aprovadoPor;
    private String recorrencia;

    public ReservationDTO() {
    }

    public ReservationDTO(Reservation reservation) {
        this.id = reservation.getId();
        this.roomsId = reservation.getRoomsId();
        this.userId = reservation.getUserId();
        this.data = reservation.getData();
        this.horaInicio = reservation.getHoraInicio();
        this.horaFim = reservation.getHoraFim();
        this.depositImage = reservation.getDepositImage();
        this.description = reservation.getDescription();
        this.recorrencia = "unica";

        String comments = reservation.getComments();
        if (comments != null && comments.contains("|")) {
            String[] parts = comments.split("\\|", -1);
            this.statusString = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : "pendente";
            this.motivoNegacao = parts.length > 1 ? parts[1] : "";
            this.aprovadoPor = parts.length > 2 ? parts[2] : "";
            if (parts.length > 3 && !parts[3].isEmpty()) {
                this.recorrencia = parts[3];
            }
        } else {
            this.statusString = comments != null && !comments.isEmpty() ? comments : "pendente";
            this.motivoNegacao = "";
            this.aprovadoPor = "";
        }
    }

    public static Reservation toEntity(ReservationDTO dto) {
        Reservation r = new Reservation();
        r.setId(dto.getId());
        r.setRoomsId(dto.getRoomsId());
        r.setUserId(dto.getUserId());
        r.setData(dto.getData());
        r.setHoraInicio(dto.getHoraInicio());
        r.setHoraFim(dto.getHoraFim());
        r.setDepositImage(dto.getDepositImage() != null && !dto.getDepositImage().isEmpty() ? dto.getDepositImage() : "empty");
        r.setDescription(dto.getDescription());
        
        String statusVal = dto.getStatusString() != null ? dto.getStatusString() : "pendente";
        String motivoVal = dto.getMotivoNegacao() != null ? dto.getMotivoNegacao() : "";
        String aprovadoVal = dto.getAprovadoPor() != null ? dto.getAprovadoPor() : "";
        String recVal = dto.getRecorrencia() != null ? dto.getRecorrencia() : "unica";
        
        r.setComments(statusVal + "|" + motivoVal + "|" + aprovadoVal + "|" + recVal);
        r.setStatus(!"cancelada".equals(statusVal) && !"negada".equals(statusVal));
        
        return r;
    }
}
