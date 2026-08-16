package com.clinica.escuta.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.clinica.escuta.model.Unit;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Integer> {
    Optional<Unit> findByName(String name);
    List<Unit> findByStatusTrue();
    List<Unit> findByStatusFalse();
    Optional <Unit> findById(Integer id);
}
