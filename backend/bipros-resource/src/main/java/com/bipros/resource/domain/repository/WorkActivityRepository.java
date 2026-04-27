package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.WorkActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkActivityRepository extends JpaRepository<WorkActivity, UUID> {

  Optional<WorkActivity> findByCode(String code);

  Optional<WorkActivity> findByNameIgnoreCase(String name);

  List<WorkActivity> findByActive(Boolean active);
}
