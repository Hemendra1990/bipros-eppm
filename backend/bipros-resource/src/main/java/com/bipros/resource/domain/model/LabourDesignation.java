package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "labour_designations",
    schema = "resource",
    uniqueConstraints = @UniqueConstraint(name = "uk_labour_designation_code", columnNames = "code"),
    indexes = {
        @Index(name = "idx_labour_designation_category", columnList = "category"),
        @Index(name = "idx_labour_designation_grade", columnList = "grade"),
        @Index(name = "idx_labour_designation_status", columnList = "status")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabourDesignation extends BaseEntity {

    @Column(nullable = false, length = 20, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String designation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LabourCategory category;

    @Column(nullable = false, length = 80)
    private String trade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private LabourGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NationalityType nationality;

    @Column(name = "experience_years_min", nullable = false)
    private Integer experienceYearsMin;

    @Column(name = "default_daily_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultDailyRate;

    @Column(nullable = false, length = 3)
    @Default
    private String currency = "OMR";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Default
    private List<String> skills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Default
    private List<String> certifications = new ArrayList<>();

    @Column(name = "key_role_summary", length = 500)
    private String keyRoleSummary;

    @Column(nullable = false, length = 20)
    @Default
    private String status = "ACTIVE";

    @Column(name = "sort_order", nullable = false)
    @Default
    private Integer sortOrder = 0;
}
