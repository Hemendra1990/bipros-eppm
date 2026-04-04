package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.Tender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TenderRepository extends JpaRepository<Tender, UUID> {
    Page<Tender> findByProjectId(UUID projectId, Pageable pageable);
    List<Tender> findByProcurementPlanId(UUID procurementPlanId);
    List<Tender> findByProjectId(UUID projectId);
}
