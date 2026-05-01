package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceTypeRepository extends JpaRepository<ResourceType, UUID> {

  Optional<ResourceType> findByCode(String code);

  Optional<ResourceType> findFirstBySystemDefaultTrueAndCode(String code);
}
