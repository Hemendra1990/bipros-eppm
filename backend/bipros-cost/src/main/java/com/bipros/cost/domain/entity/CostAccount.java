package com.bipros.cost.domain.entity;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "cost_accounts", schema = "cost")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CostAccount extends BaseEntity {

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
