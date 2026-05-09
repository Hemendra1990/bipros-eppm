package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialCategoryMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialCategoryMasterRepository extends JpaRepository<MaterialCategoryMaster, UUID> {

  Optional<MaterialCategoryMaster> findByCode(String code);
}
