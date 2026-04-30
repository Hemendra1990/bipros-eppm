package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.UserResourceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserResourceRoleRepository extends JpaRepository<UserResourceRole, UUID> {

  List<UserResourceRole> findByResourceRoleId(UUID resourceRoleId);

  List<UserResourceRole> findByUserId(UUID userId);

  Optional<UserResourceRole> findByUserIdAndResourceRoleId(UUID userId, UUID resourceRoleId);

  boolean existsByUserIdAndResourceRoleId(UUID userId, UUID resourceRoleId);

  long countByResourceRoleId(UUID resourceRoleId);
}
