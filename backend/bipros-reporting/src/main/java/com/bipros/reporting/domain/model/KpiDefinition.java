package com.bipros.reporting.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kpi_definitions", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KpiDefinition extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "formula", columnDefinition = "TEXT")
    @Lob
    private String formula;

    @Column(name = "unit")
    private String unit;

    @Column(name = "green_threshold")
    private Double greenThreshold;

    @Column(name = "amber_threshold")
    private Double amberThreshold;

    @Column(name = "red_threshold")
    private Double redThreshold;

    @Column(name = "module_source")
    private String moduleSource;

    @Column(name = "is_active")
    private Boolean isActive;
}
