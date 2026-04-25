package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.ContractAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContractAttachmentRepository extends JpaRepository<ContractAttachment, UUID> {

    List<ContractAttachment> findByContractIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
        UUID contractId, AttachmentEntityType entityType, UUID entityId);

    List<ContractAttachment> findByContractIdOrderByCreatedAtDesc(UUID contractId);

    long countByContractIdAndEntityTypeAndEntityId(
        UUID contractId, AttachmentEntityType entityType, UUID entityId);

    /**
     * Bulk count attachments grouped by entityId for a given contract + type.
     * Used to populate {@code attachmentCount} badges on milestone/VO/bond list rows
     * without N+1 queries.
     */
    @Query("""
        SELECT a.entityId AS entityId, COUNT(a) AS cnt
        FROM ContractAttachment a
        WHERE a.contractId = :contractId
          AND a.entityType = :entityType
          AND a.entityId IN :entityIds
        GROUP BY a.entityId
    """)
    List<EntityCount> countByEntities(
        @Param("contractId") UUID contractId,
        @Param("entityType") AttachmentEntityType entityType,
        @Param("entityIds") Collection<UUID> entityIds);

    interface EntityCount {
        UUID getEntityId();
        Long getCnt();
    }
}
