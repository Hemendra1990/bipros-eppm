package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "permit_attachment", schema = "permit", indexes = {
        @Index(name = "ix_permit_attachment_permit", columnList = "permit_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitAttachment extends BaseEntity {

    @Column(name = "permit_id", nullable = false)
    private UUID permitId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttachmentKind kind = AttachmentKind.OTHER;
}
