package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {

  Page<Resource> findByUpdatedAtAfter(Instant since, Pageable pageable);


  List<Resource> findByResourceType(ResourceType resourceType);

  List<Resource> findByParentIdIsNull();

  List<Resource> findByParentId(UUID parentId);

  List<Resource> findByStatus(ResourceStatus status);

  Optional<Resource> findByCode(String code);

  long countByResourceTypeDefId(UUID resourceTypeDefId);
}
