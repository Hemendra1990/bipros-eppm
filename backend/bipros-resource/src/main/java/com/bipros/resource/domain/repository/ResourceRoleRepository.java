package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceRoleRepository extends JpaRepository<ResourceRole, UUID> {

  Optional<ResourceRole> findByCode(String code);

  List<ResourceRole> findByResourceType_Code(String typeCode);

  List<ResourceRole> findByResourceType_Id(UUID typeId);

  long countByResourceType_Id(UUID typeId);
}
