package com.bipros.integration.repository;

import com.bipros.integration.model.GstnVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GstnVerificationRepository extends JpaRepository<GstnVerification, UUID> {
    Optional<GstnVerification> findByGstin(String gstin);

    Page<GstnVerification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
