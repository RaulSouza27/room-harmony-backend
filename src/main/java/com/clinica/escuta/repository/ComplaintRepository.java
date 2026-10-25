package com.clinica.escuta.repository;

import com.clinica.escuta.model.Complaint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Integer>, JpaSpecificationExecutor<Complaint> {
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findByUserIdOrderByCreatedAtDesc(Integer userId);
    Page<Complaint> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);
    long countBySenderDailyHash(String senderDailyHash);
    long countBySolvedTrue();
    long countBySolvedFalse();
}
