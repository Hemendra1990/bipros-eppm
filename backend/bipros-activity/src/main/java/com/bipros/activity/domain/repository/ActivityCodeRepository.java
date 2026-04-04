package com.bipros.activity.domain.repository;

import com.bipros.activity.domain.model.ActivityCode;
import com.bipros.activity.domain.model.CodeScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityCodeRepository extends JpaRepository<ActivityCode, UUID> {
  List<ActivityCode> findByScope(CodeScope scope);

  List<ActivityCode> findByProjectId(UUID projectId);

  List<ActivityCode> findByEpsNodeId(UUID epsNodeId);
}
