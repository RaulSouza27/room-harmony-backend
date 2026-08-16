package com.clinica.escuta.DTO;

import com.clinica.escuta.model.Room;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RoomDTO {
    private Integer id;
    private String name;
    private Integer unitId;
    private boolean status;
    private String description;
    private String comments;
    private List<String> photos;

    public RoomDTO() {
        this.photos = new ArrayList<>();
    }

    public RoomDTO(Room room) {
        this.id = room.getId();
        this.name = room.getName();
        this.unitId = room.getUnitId();
        this.status = Boolean.TRUE.equals(room.getStatus());
        this.description = room.getDescription();
        this.comments = room.getComments();
        
        this.photos = new ArrayList<>();
        if (room.getPhoto1() != null && !room.getPhoto1().isEmpty()) {
            this.photos.add(room.getPhoto1());
        }
        if (room.getPhoto2() != null && !room.getPhoto2().isEmpty()) {
            this.photos.add(room.getPhoto2());
        }
        if (room.getPhoto3() != null && !room.getPhoto3().isEmpty()) {
            this.photos.add(room.getPhoto3());
        }
        if (room.getPhoto4() != null && !room.getPhoto4().isEmpty()) {
            this.photos.add(room.getPhoto4());
        }
    }
}
