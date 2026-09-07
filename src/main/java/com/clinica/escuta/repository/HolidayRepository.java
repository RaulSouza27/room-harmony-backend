package com.clinica.escuta.repository;

import com.clinica.escuta.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Integer> {

    List<Holiday> findByStatusTrue();

    List<Holiday> findByUnitIdOrUnitIdIsNull(Integer unitId);

    List<Holiday> findByStatusTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate endDate, LocalDate startDate);
}
