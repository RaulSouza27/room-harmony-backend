package com.clinica.escuta.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.clinica.escuta.model.Room;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {
    Optional<Room> findByName(String name);
    List<Room> findByStatusTrue();
    List<Room> findByStatusFalse();
    List<Room> findByUnitId(Integer unitId);

    @Query("SELECT new com.clinica.escuta.model.Room(r.id, r.name, r.unitId, r.status, r.description, r.comments, " +
           "CASE WHEN r.photo1 IS NOT NULL AND r.photo1 <> '' THEN '1' ELSE NULL END, " +
           "CASE WHEN r.photo2 IS NOT NULL AND r.photo2 <> '' THEN '1' ELSE NULL END, " +
           "CASE WHEN r.photo3 IS NOT NULL AND r.photo3 <> '' THEN '1' ELSE NULL END, " +
           "CASE WHEN r.photo4 IS NOT NULL AND r.photo4 <> '' THEN '1' ELSE NULL END) FROM Room r")
    List<Room> findAllLightweight();
}
