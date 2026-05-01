package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {

  Optional<Resource> findByCode(String code);

  List<Resource> findByResourceType_Code(String typeCode);

  List<Resource> findByResourceType_Id(UUID typeId);

  List<Resource> findByRole_Id(UUID roleId);

  List<Resource> findByParentIdIsNull();

  List<Resource> findByParentId(UUID parentId);

  List<Resource> findByStatus(ResourceStatus status);

  List<Resource> findByUserIdIn(Collection<UUID> userIds);

  long countByResourceType_Id(UUID typeId);

  long countByRole_Id(UUID roleId);
}
