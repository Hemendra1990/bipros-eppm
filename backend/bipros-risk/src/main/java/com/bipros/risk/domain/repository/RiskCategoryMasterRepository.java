package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.RiskCategoryMaster;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskCategoryMasterRepository extends JpaRepository<RiskCategoryMaster, UUID> {

    @EntityGraph(attributePaths = "type")
    Optional<RiskCategoryMaster> findByCode(String code);

    @EntityGraph(attributePaths = "type")
    List<RiskCategoryMaster> findByActiveTrueOrderBySortOrderAsc();

    @EntityGraph(attributePaths = "type")
    List<RiskCategoryMaster> findByTypeIdAndActiveTrueOrderBySortOrderAsc(UUID typeId);

    @EntityGraph(attributePaths = "type")
    List<RiskCategoryMaster> findByTypeIdAndIndustryInAndActiveTrueOrderBySortOrderAsc(
        UUID typeId, Collection<Industry> industries);

    @EntityGraph(attributePaths = "type")
    List<RiskCategoryMaster> findByIndustryInAndActiveTrueOrderBySortOrderAsc(
        Collection<Industry> industries);

    long countByTypeId(UUID typeId);

    boolean existsByCode(String code);
}
