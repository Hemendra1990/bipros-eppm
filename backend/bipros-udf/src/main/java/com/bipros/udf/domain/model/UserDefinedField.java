package com.bipros.udf.domain.model;

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
@Table(name = "user_defined_fields", schema = "udf")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDefinedField extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UdfDataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UdfSubject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UdfScope scope = UdfScope.GLOBAL;

    @Column
    private UUID projectId;

    @Column(nullable = false)
    private Boolean isFormula = false;

    @Column(columnDefinition = "TEXT")
    private String formulaExpression;

    @Column
    private String defaultValue;

    @Column
    private int sortOrder;
}
