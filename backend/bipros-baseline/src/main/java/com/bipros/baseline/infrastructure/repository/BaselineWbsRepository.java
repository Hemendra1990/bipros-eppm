package com.bipros.baseline.infrastructure.repository;

import com.bipros.baseline.domain.BaselineWbs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BaselineWbsRepository extends JpaRepository<BaselineWbs, UUID> {

  List<BaselineWbs> findByBaselineId(UUID baselineId);

  void deleteByBaselineId(UUID baselineId);
}
