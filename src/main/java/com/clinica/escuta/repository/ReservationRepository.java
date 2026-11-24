package com.clinica.escuta.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.clinica.escuta.model.Reservation;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer>, JpaSpecificationExecutor<Reservation> {
    String LIGHT_PROJECTION = "SELECT new com.clinica.escuta.model.Reservation(" +
            "r.id, r.roomsId, r.userId, r.data, r.horaInicio, r.horaFim, " +
            "CASE WHEN r.depositImage IS NULL OR r.depositImage = '' OR LOWER(r.depositImage) = 'empty' THEN 'empty' " +
            "     WHEN LOWER(r.depositImage) = 'reserva_especial_admin' THEN 'reserva_especial_admin' " +
            "     ELSE 'has_receipt' END, " +
            "r.description, r.comments, r.status) FROM Reservation r ";

    List<Reservation> findByStatusTrue();
    List<Reservation> findByDataBetween(LocalDate startDate, LocalDate endDate);
    List<Reservation> findByUserId(Integer userId);
    List<Reservation> findByRoomsId(Integer roomsId);
    List<Reservation> findByRoomsIdIn(List<Integer> roomsIds);
    List<Reservation> findByRoomsIdInAndDataBetween(List<Integer> roomsIds, LocalDate startDate, LocalDate endDate);

    @Query(LIGHT_PROJECTION + "WHERE r.roomsId = :roomsId AND r.data = :data AND r.status = true")
    List<Reservation> findByRoomsIdAndDataAndStatusTrue(@Param("roomsId") Integer roomsId, @Param("data") LocalDate data);

    List<Reservation> findByDataBetweenAndStatusTrue(LocalDate startDate, LocalDate endDate);

    @Query(LIGHT_PROJECTION)
    List<Reservation> findAllLightweight();

    @Query(LIGHT_PROJECTION + "WHERE r.status = true")
    List<Reservation> findByStatusTrueLightweight();

    @Query(LIGHT_PROJECTION + "WHERE r.data BETWEEN :startDate AND :endDate")
    List<Reservation> findByDataBetweenLightweight(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query(LIGHT_PROJECTION + "WHERE r.userId = :userId")
    List<Reservation> findByUserIdLightweight(@Param("userId") Integer userId);

    @Query(LIGHT_PROJECTION + "WHERE r.roomsId = :roomsId")
    List<Reservation> findByRoomsIdLightweight(@Param("roomsId") Integer roomsId);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.status = true AND (" +
           "r.comments LIKE 'pendente|%' OR " +
           "r.comments LIKE 'solicitacao_cancelamento|%' OR " +
           "r.comments LIKE 'reagendar|%')")
    long countPendingActionable();
}

