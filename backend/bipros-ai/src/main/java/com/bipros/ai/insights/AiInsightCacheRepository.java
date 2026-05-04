package com.bipros.ai.insights;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiInsightCacheRepository extends JpaRepository<AiInsightCache, UUID> {
    Optional<AiInsightCache> findByProjectIdAndTab(UUID projectId, String tab);

    void deleteByProjectIdAndTab(UUID projectId, String tab);

    void deleteByCreatedAtBefore(Instant cutoff);
}
