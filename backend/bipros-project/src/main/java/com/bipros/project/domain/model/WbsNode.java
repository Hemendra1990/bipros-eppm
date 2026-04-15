package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import com.bipros.common.model.HierarchyNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // --- IC-PMS M1 extensions (sample data spec) ---

    /** WBS hierarchy level 1-4 (1=Programme, 2=Node, 3=Package, 4=Work Package). */
    @Column(name = "wbs_level")
    private Integer wbsLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "wbs_type", length = 20)
    private WbsType wbsType;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 20)
    private WbsPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "wbs_status", length = 20)
    private WbsStatus wbsStatus;

    /** FK to organisations.id — the org responsible for delivering this WBS element. */
    @Column(name = "responsible_organisation_id")
    private UUID responsibleOrganisationId;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_finish")
    private LocalDate plannedFinish;

    /** Budget in crores (INR) — Excel fidelity unit. */
    @Column(name = "budget_crores", precision = 14, scale = 2)
    private BigDecimal budgetCrores;

    /** Denormalised GIS polygon ID (e.g. "POLY-N03-P01"); detailed geometry lives in bipros-gis. */
    @Column(name = "gis_polygon_id", length = 40)
    private String gisPolygonId;

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
