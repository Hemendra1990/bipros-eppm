package com.bipros.udf.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "udf_values", schema = "udf",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_defined_field_id", "entity_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UdfValue extends BaseEntity {

    @Column(nullable = false)
    private UUID userDefinedFieldId;

    @Column(nullable = false)
    private UUID entityId;

    @Column(columnDefinition = "TEXT")
    private String textValue;

    @Column
    private Double numberValue;

    @Column
    private BigDecimal costValue;

    @Column
    private LocalDate dateValue;

    @Enumerated(EnumType.STRING)
    @Column
    private IndicatorColor indicatorValue;

    @Column
    private String codeValue;
}
