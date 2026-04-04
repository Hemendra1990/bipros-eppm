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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "drawing_registers", schema = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DrawingRegister extends BaseEntity {

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = true)
    private UUID documentId;

    @Column(nullable = false, length = 100, unique = true)
    private String drawingNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrawingDiscipline discipline;

    @Column(nullable = false, length = 50)
    private String revision;

    @Column(nullable = false)
    private LocalDate revisionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrawingStatus status = DrawingStatus.PRELIMINARY;

    @Column(nullable = false, length = 100)
    private String packageCode;

    @Column(nullable = false, length = 50)
    private String scale;
}
