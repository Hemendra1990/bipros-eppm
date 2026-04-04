package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.VariationOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VariationOrderRepository extends JpaRepository<VariationOrder, UUID> {
    List<VariationOrder> findByContractId(UUID contractId);
}
