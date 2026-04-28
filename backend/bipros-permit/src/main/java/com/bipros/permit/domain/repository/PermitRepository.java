package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermitRepository extends JpaRepository<Permit, UUID>, JpaSpecificationExecutor<Permit> {

    Optional<Permit> findByPermitCode(String permitCode);

    Optional<Permit> findByQrToken(String qrToken);

    long countByProjectIdAndStatus(UUID projectId, PermitStatus status);

    long countByProjectIdAndStatusIn(UUID projectId, List<PermitStatus> statuses);

    @Query("select p from Permit p where p.status in :statuses and p.endAt < :cutoff")
    List<Permit> findExpired(@Param("statuses") List<PermitStatus> statuses, @Param("cutoff") Instant cutoff);

    @Query("select p from Permit p where p.status = :status and p.updatedAt < :cutoff")
    List<Permit> findStuck(@Param("status") PermitStatus status, @Param("cutoff") Instant cutoff);

    @Query("select p from Permit p where p.projectId = :projectId order by p.createdAt desc")
    Page<Permit> findByProjectIdRecent(@Param("projectId") UUID projectId, Pageable pageable);
}
