package com.bipros.cost.domain.entity;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "project_funding", schema = "cost")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectFunding extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "funding_source_id", nullable = false)
    private UUID fundingSourceId;

    @Column(name = "wbs_node_id")
    private UUID wbsNodeId;

    @Column(name = "allocated_amount", precision = 19, scale = 2)
    private BigDecimal allocatedAmount;
}
