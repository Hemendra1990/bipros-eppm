package com.bipros.integration.repository;

import com.bipros.integration.model.PfmsFundTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PfmsFundTransferRepository extends JpaRepository<PfmsFundTransfer, UUID> {
    Page<PfmsFundTransfer> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Optional<PfmsFundTransfer> findBySanctionOrderNumber(String sanctionOrderNumber);

    Optional<PfmsFundTransfer> findByPfmsReferenceNumber(String pfmsReferenceNumber);
}
