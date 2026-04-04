package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.RaBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaBillRepository extends JpaRepository<RaBill, UUID> {
    List<RaBill> findByProjectIdOrderByBillNumberDesc(UUID projectId);
    Optional<RaBill> findByBillNumber(String billNumber);
    List<RaBill> findByProjectIdAndStatus(UUID projectId, RaBill.RaBillStatus status);
}
