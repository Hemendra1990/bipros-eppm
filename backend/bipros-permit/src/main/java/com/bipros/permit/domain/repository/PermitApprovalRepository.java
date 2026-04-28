package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermitApprovalRepository extends JpaRepository<PermitApproval, UUID> {
    List<PermitApproval> findByPermitIdOrderByStepNoAsc(UUID permitId);

    Optional<PermitApproval> findByPermitIdAndStepNo(UUID permitId, int stepNo);
}
