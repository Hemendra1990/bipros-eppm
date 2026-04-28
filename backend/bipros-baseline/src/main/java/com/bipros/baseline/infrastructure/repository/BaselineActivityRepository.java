package com.bipros.baseline.infrastructure.repository;

import com.bipros.baseline.domain.BaselineActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BaselineActivityRepository extends JpaRepository<BaselineActivity, UUID> {

  Page<BaselineActivity> findByUpdatedAtAfter(Instant since, Pageable pageable);


  List<BaselineActivity> findByBaselineId(UUID baselineId);

  Optional<BaselineActivity> findByBaselineIdAndActivityId(UUID baselineId, UUID activityId);
}
