package com.clinica.escuta.repository;

import com.clinica.escuta.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Integer> {
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findByUserIdOrderByCreatedAtDesc(Integer userId);
    long countBySenderDailyHash(String senderDailyHash);
}

