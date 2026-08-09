package com.salas.psi.harmony.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.salas.psi.harmony.model.Room;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {
    Optional<Room> findByName(String name);
    List<Room> findByStatusTrue();
    List<Room> findByStatusFalse();
    List<Room> findByUnitId(Integer unitId);
}
