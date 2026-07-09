package com.bipros.ai.agent.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentBudgetUsageRepository extends JpaRepository<AgentBudgetUsage, UUID> {

    Optional<AgentBudgetUsage> findByProjectIdAndUsageDate(UUID projectId, LocalDate usageDate);

    /** Row-locked lookup for the atomic reserve/record path (multi-node safe via PESSIMISTIC_WRITE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AgentBudgetUsage u where u.projectId = :projectId and u.usageDate = :usageDate")
    Optional<AgentBudgetUsage> lockByProjectIdAndUsageDate(@Param("projectId") UUID projectId,
                                                           @Param("usageDate") LocalDate usageDate);
}
