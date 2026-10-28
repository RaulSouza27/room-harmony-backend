package com.clinica.escuta.repository;

import com.clinica.escuta.model.CreditPackage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CreditPackageRepository extends JpaRepository<CreditPackage, Integer>, JpaSpecificationExecutor<CreditPackage> {

    List<CreditPackage> findByUserIdOrderByCreatedAtDesc(Integer userId);

    List<CreditPackage> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);

    /**
     * Busca os pacotes aprovados e com saldo de um locatário em uma unidade, ordenados pelo que vence antes (FEFO),
     * aplicando lock pessimista para evitar gasto duplo em requisições concorrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CreditPackage p WHERE p.userId = :userId AND p.unitId = :unitId AND p.status = 'APPROVED' AND p.remainingCredits > 0 ORDER BY p.expiresAt ASC, p.id ASC")
    List<CreditPackage> findActivePackagesForUpdate(@Param("userId") Integer userId, @Param("unitId") Integer unitId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CreditPackage p WHERE p.id IN :ids")
    List<CreditPackage> findAllByIdsForUpdate(@Param("ids") List<Integer> ids);

    @Query("SELECT p FROM CreditPackage p WHERE p.status = 'APPROVED' AND p.expiresAt < :now")
    List<CreditPackage> findExpiredApprovedPackages(@Param("now") LocalDateTime now);

    @Query("SELECT p FROM CreditPackage p WHERE p.userId = :userId AND p.status = 'APPROVED' AND p.expiresAt < :now")
    List<CreditPackage> findUserExpiredApprovedPackages(@Param("userId") Integer userId, @Param("now") LocalDateTime now);
}

