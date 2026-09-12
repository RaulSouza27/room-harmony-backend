package com.clinica.escuta.DTO;

import com.clinica.escuta.model.Holiday;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class HolidayDTO {
    private Integer id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer unitId;
    private String unitName;
    private String description;
    private Boolean status = true;

    public HolidayDTO(Holiday holiday) {
        if (holiday != null) {
            this.id = holiday.getId();
            this.name = holiday.getName();
            this.startDate = holiday.getStartDate();
            this.endDate = holiday.getEndDate();
            this.unitId = holiday.getUnitId();
            this.description = holiday.getDescription();
            this.status = holiday.getStatus();
        }
    }

    public HolidayDTO(Holiday holiday, String unitName) {
        this(holiday);
        this.unitName = unitName;
    }
}
