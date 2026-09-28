package com.clinica.escuta.DTO;

import com.clinica.escuta.model.Profissions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfissionsDTO {
    private Integer id;
    private String profission;

    public ProfissionsDTO(Profissions profissions)
    {
        this.id = profissions.getId();
        this.profission = profissions.getProfission();
    }

    public static Profissions toEntity(ProfissionsDTO dto) {
        Profissions p = new Profissions();
        p.setId(dto.getId());
        p.setProfission(dto.getProfission());

        return p;
    }
}
