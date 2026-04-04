package com.bipros.admin.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "units_of_measure", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnitOfMeasure extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String abbreviation;

    @Column(length = 50)
    private String category;
}
