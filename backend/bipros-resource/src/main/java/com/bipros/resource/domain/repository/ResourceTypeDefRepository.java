package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceTypeDef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceTypeDefRepository extends JpaRepository<ResourceTypeDef, UUID> {

  Optional<ResourceTypeDef> findByCode(String code);

  List<ResourceTypeDef> findByActive(Boolean active);

  List<ResourceTypeDef> findByBaseCategory(ResourceType baseCategory);

  Optional<ResourceTypeDef> findFirstByBaseCategoryAndSystemDefaultTrue(ResourceType baseCategory);
}
