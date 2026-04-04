package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import com.bipros.common.model.HierarchyNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "wbs_nodes", schema = "project")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WbsNode extends BaseEntity implements HierarchyNode {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "obs_node_id")
    private UUID obsNodeId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "summary_duration")
    private Double summaryDuration;

    @Column(name = "summary_percent_complete")
    private Double summaryPercentComplete;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class")
    private AssetClass assetClass;

    @Override
    public int getSortOrder() {
        return sortOrder != null ? sortOrder : 0;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getParentId() {
        return parentId;
    }
}
