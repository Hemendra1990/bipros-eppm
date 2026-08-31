package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.rate.MaterialRateMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialRateMasterRepository extends JpaRepository<MaterialRateMaster, UUID> {

  Optional<MaterialRateMaster> findByCategoryIdAndSpecGrade(UUID categoryId, String specGrade);

  List<MaterialRateMaster> findByActiveTrue();

  long countByCategoryId(UUID categoryId);
}
