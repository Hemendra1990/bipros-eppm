package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.manpower.ManpowerMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManpowerMasterRepository extends JpaRepository<ManpowerMaster, UUID> {

  Optional<ManpowerMaster> findByEmployeeCode(String code);

  /** HR-tree lookup: who reports to this manpower record. */
  List<ManpowerMaster> findByReportingManagerId(UUID reportingManagerId);

  long countByCategory(String category);

  long countBySubCategory(String subCategory);

  long countByEmploymentType(String employmentType);

  long countByNationality(String nationality);
}
