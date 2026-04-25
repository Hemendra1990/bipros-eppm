package com.bipros.contract.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Polymorphic attachment row backing every level of the contract bounded context
 * (Contract, ContractMilestone, VariationOrder, PerformanceBond).
 * Referential integrity to the parent entity is enforced at the application layer
 * via the {@code entityType} + {@code entityId} discriminator.
 */
@Entity
@Table(
    name = "contract_attachments",
    schema = "contract",
    indexes = {
        @Index(name = "idx_attach_contract", columnList = "contract_id"),
        @Index(name = "idx_attach_entity", columnList = "contract_id, entity_type, entity_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContractAttachment extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AttachmentEntityType entityType;

    /** When entityType=CONTRACT this equals contractId; otherwise the milestone/VO/bond id. */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** Relative path under the configured storage root. */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, length = 50)
    private ContractAttachmentType attachmentType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;
}
