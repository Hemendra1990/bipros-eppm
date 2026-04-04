package com.bipros.admin.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "currencies", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Currency extends BaseEntity {

    @Column(nullable = false, unique = true, length = 3)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 5)
    private String symbol;

    @Column
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column
    private Boolean isBaseCurrency = false;

    @Column
    private Integer decimalPlaces = 2;
}
