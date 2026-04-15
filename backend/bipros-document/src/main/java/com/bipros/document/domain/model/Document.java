package com.bipros.document.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "documents", schema = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Document extends BaseEntity {

    @Column(nullable = false)
    private UUID folderId;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 100, unique = true)
    private String documentNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(nullable = false)
    private Integer currentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.DRAFT;

    /** IC-PMS M6 document type (DRAWING / SPECIFICATION / RFI / …). */
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 40)
    private DocumentType documentType;

    /** IC-PMS M6 drawing discipline (used when documentType=DRAWING). */
    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", length = 30)
    private DrawingDiscipline discipline;

    /** Denormalised last transmittal number for quick display on the register grid. */
    @Column(name = "transmittal_number", length = 60)
    private String transmittalNumber;

    @Column(columnDefinition = "TEXT")
    private String tags;
}
