package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.AnalyticsQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsQueryRepository extends JpaRepository<AnalyticsQuery, UUID> {
  List<AnalyticsQuery> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
