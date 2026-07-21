package com.bipros.ai.agent.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Create the (scope, day) counter row only if it does not exist yet. A sweep fires every agent
     * concurrently, so on the day's first run several threads find no row and all try to insert it.
     * {@code on conflict do nothing} lets the losers continue: a raised unique-constraint violation
     * would abort the whole PostgreSQL transaction, leaving no way to re-read the winner's row.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into ai.agent_budget_usage
                (id, project_id, usage_date, tokens_reserved, tokens_used, run_count,
                 version, created_at, updated_at, created_by, updated_by)
            values (gen_random_uuid(), :projectId, :usageDate, 0, 0, 0, 0, now(), now(), 'system', 'system')
            on conflict (project_id, usage_date) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("projectId") UUID projectId, @Param("usageDate") LocalDate usageDate);
}
