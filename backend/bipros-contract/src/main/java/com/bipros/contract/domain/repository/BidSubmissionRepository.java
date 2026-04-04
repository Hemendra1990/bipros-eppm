package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.BidSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BidSubmissionRepository extends JpaRepository<BidSubmission, UUID> {
    Page<BidSubmission> findByTenderId(UUID tenderId, Pageable pageable);
    List<BidSubmission> findByTenderId(UUID tenderId);
}
