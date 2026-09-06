package com.clinica.escuta.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import com.clinica.escuta.model.Reservation;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer>, JpaSpecificationExecutor<Reservation> {
    List<Reservation> findByStatusTrue();
    List<Reservation> findByDataBetween(LocalDate startDate, LocalDate endDate);
    List<Reservation> findByUserId(Integer userId);
    List<Reservation> findByRoomsId(Integer roomsId);
    List<Reservation> findByRoomsIdIn(List<Integer> roomsIds);
    List<Reservation> findByRoomsIdInAndDataBetween(List<Integer> roomsIds, LocalDate startDate, LocalDate endDate);
}

