package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  /**
   * DPR history referencing this role across the three DPR line tables. Native cross-schema
   * query (the DPR tables belong to the project context — same DB, no repo dependency);
   * used by the delete guard so a role with site history can't be hard-deleted.
   */
  @Query(value = "SELECT (SELECT count(*) FROM project.dpr_manpower WHERE role_id = :roleId)"
      + " + (SELECT count(*) FROM project.dpr_equipment WHERE role_id = :roleId)"
      + " + (SELECT count(*) FROM project.dpr_material WHERE role_id = :roleId)",
      nativeQuery = true)
  long countDprUsage(@Param("roleId") UUID roleId);
}
