package com.clinica.escuta.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiringReservationAlertDTO {
    private String seriesKey;
    private Integer lastReservationId;
    private Integer userId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private Integer roomId;
    private String roomName;
    private Integer unitId;
    private String unitName;
    private String recorrencia;
    private String recorrenciaLabel;
    private String dayOfWeekLabel;
    private String horaInicio;
    private String horaFim;
    private LocalDate lastDate;
    private String lastDateFormatted;
    private long daysUntilExpiration;
    private String urgencyLevel; // "critical" | "warning"
    private String alertTitle;
    private String alertMessage;
    private String whatsappMessage;
    private LocalDate nextStartDate;
    private String nextStartDateFormatted;
    private String nextCycleLabel;
}

