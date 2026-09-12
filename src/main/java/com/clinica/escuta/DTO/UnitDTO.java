package com.clinica.escuta.DTO;

import com.clinica.escuta.model.Unit;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class UnitDTO {
    private Integer id;
    private String name;
    private String address;
    private boolean status;
    private Map<String, DayScheduleDTO> businessHours;

    public UnitDTO() {
    }

    public UnitDTO(Unit unit) {
        this.id = unit.getId();
        this.name = unit.getName();
        this.address = unit.getAddress();
        this.status = Boolean.TRUE.equals(unit.getStatus());
        this.businessHours = unit.getBusinessHours() != null ? unit.getBusinessHours() : createDefaultBusinessHours();
    }

    public static Map<String, DayScheduleDTO> createDefaultBusinessHours() {
        Map<String, DayScheduleDTO> map = new HashMap<>();
        map.put("0", new DayScheduleDTO(false, null, null));
        map.put("1", new DayScheduleDTO(true, "08:00", "18:00"));
        map.put("2", new DayScheduleDTO(true, "08:00", "18:00"));
        map.put("3", new DayScheduleDTO(true, "08:00", "18:00"));
        map.put("4", new DayScheduleDTO(true, "08:00", "18:00"));
        map.put("5", new DayScheduleDTO(true, "08:00", "18:00"));
        map.put("6", new DayScheduleDTO(true, "08:00", "12:00"));
        return map;
    }
}
