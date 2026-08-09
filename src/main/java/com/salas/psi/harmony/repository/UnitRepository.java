package com.salas.psi.harmony.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.salas.psi.harmony.model.Unit;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Integer> {
    Optional<Unit> findByName(String name);
    List<Unit> findByStatusTrue();
    List<Unit> findByStatusFalse();
    Optional <Unit> findById(Integer id);
}
