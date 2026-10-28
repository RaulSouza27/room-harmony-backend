package com.clinica.escuta.repository;

import com.clinica.escuta.model.CreditTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Integer>, JpaSpecificationExecutor<CreditTransaction> {

    List<CreditTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Integer userId);

    Page<CreditTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Integer userId, Pageable pageable);

    List<CreditTransaction> findByReservationIdOrderByIdAsc(Integer reservationId);
}

