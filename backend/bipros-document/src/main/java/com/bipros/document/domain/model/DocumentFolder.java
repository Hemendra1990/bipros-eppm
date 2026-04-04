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
@Table(name = "document_folders", schema = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFolder extends BaseEntity {

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = true)
    private UUID parentId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String code;

    @Enumerated(EnumType.STRING)
    private DocumentCategory category;

    @Column(nullable = true)
    private UUID wbsNodeId;

    @Column(nullable = false)
    private Integer sortOrder = 0;
}
