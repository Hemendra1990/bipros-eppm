package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.rate.ManpowerRateMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManpowerRateMasterRepository extends JpaRepository<ManpowerRateMaster, UUID> {

  Optional<ManpowerRateMaster> findByRoleIdAndCategoryIdAndGradeId(
      UUID roleId, UUID categoryId, UUID gradeId);

  List<ManpowerRateMaster> findByActiveTrue();

  long countByRoleId(UUID roleId);

  long countByCategoryId(UUID categoryId);

  long countByGradeId(UUID gradeId);
}
