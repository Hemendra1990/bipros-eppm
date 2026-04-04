package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "labour_returns",
    schema = "resource",
    indexes = {
        @Index(name = "idx_labour_return_project_id", columnList = "project_id"),
        @Index(name = "idx_labour_return_date", columnList = "return_date"),
        @Index(name = "idx_labour_return_skill_category", columnList = "skill_category"),
        @Index(name = "idx_labour_return_wbs_node_id", columnList = "wbs_node_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabourReturn extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "contractor_name", nullable = false, length = 200)
  private String contractorName;

  @Column(name = "return_date", nullable = false)
  private LocalDate returnDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "skill_category", nullable = false, length = 30)
  private SkillCategory skillCategory;

  @Column(name = "head_count", nullable = false)
  private Integer headCount;

  @Column(name = "man_days", nullable = false)
  private Double manDays;

  @Column(name = "wbs_node_id")
  private UUID wbsNodeId;

  @Column(name = "site_location", length = 200)
  private String siteLocation;

  @Column(name = "remarks", columnDefinition = "text")
  private String remarks;
}
