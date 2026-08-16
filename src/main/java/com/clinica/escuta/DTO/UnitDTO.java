package com.clinica.escuta.DTO;

import com.clinica.escuta.model.Unit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnitDTO {
    private Integer id;
    private String name;
    private String address;
    private boolean status;

    public UnitDTO() {
    }

    public UnitDTO(Unit unit) {
        this.id = unit.getId();
        this.name = unit.getName();
        this.address = unit.getAddress();
        this.status = Boolean.TRUE.equals(unit.getStatus());
    }
}
