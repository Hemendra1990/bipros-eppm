package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.GradeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradeMasterRepository extends JpaRepository<GradeMaster, UUID> {

  Optional<GradeMaster> findByCode(String code);

  Optional<GradeMaster> findByName(String name);
}
