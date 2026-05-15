package com.semicolon.healing_service.repository;

import com.semicolon.healing_service.model.HealingAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealingActionRepository extends JpaRepository<HealingAction, Long> {

    List<HealingAction> findByServiceNameOrderByExecutedAtDesc(String serviceName);

    List<HealingAction> findByStatusOrderByExecutedAtDesc(String status);

    @Modifying
    @Query("UPDATE HealingAction h SET h.status = ?2, h.completedAt = CURRENT_TIMESTAMP WHERE h.id = ?1")
    int updateStatus(Long id, String status);

    List<HealingAction> findTop20ByServiceNameOrderByExecutedAtDesc(String serviceName);

    List<HealingAction> findTop50ByOrderByExecutedAtDesc();

    long countByServiceName(String serviceName);
}
