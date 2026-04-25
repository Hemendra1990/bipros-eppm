package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.Role;
import com.bipros.resource.domain.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceRoleRepository extends JpaRepository<Role, UUID> {

  List<Role> findByResourceType(ResourceType resourceType);

  Optional<Role> findByCode(String code);

  long countByResourceTypeDefId(UUID resourceTypeDefId);
}
