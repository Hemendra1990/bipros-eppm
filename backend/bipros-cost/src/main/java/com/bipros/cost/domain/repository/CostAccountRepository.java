package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.CostAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CostAccountRepository extends JpaRepository<CostAccount, UUID> {
    Optional<CostAccount> findByCode(String code);
    List<CostAccount> findByParentIdOrderBySortOrder(UUID parentId);
    List<CostAccount> findAllByOrderBySortOrder();
}
