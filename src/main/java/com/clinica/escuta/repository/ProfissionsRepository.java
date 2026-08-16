package com.clinica.escuta.repository;

import com.clinica.escuta.model.Profissions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProfissionsRepository extends JpaRepository<Profissions, Integer> {
    List<Profissions> findAllByOrderByProfissionAsc();
}
