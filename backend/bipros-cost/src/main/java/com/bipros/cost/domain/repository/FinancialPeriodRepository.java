package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.FinancialPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, UUID> {
    List<FinancialPeriod> findAllByOrderBySortOrder();
    List<FinancialPeriod> findByIsClosedFalseOrderBySortOrder();
}
