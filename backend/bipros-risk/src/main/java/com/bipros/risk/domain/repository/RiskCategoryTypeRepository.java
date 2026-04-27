package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.RiskCategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskCategoryTypeRepository extends JpaRepository<RiskCategoryType, UUID> {

    Optional<RiskCategoryType> findByCode(String code);

    List<RiskCategoryType> findByActiveTrueOrderBySortOrderAsc();

    boolean existsByCode(String code);
}
