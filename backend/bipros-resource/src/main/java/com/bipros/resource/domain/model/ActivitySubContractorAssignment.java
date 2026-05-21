package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Links an activity to a sub-contractor master row with planned units. Stored independently of
 * {@code ResourceAssignment} — appears as the 4th section in the Resource Demand panel.
 */
@Entity
@Table(
    name = "activity_sub_contractor_assignments",
    schema = "resource",
    indexes = {
        @Index(name = "idx_act_subcon_activity_id", columnList = "activity_id"),
        @Index(name = "idx_act_subcon_project_id", columnList = "project_id"),
        @Index(name = "idx_act_subcon_sub_contractor_master_id", columnList = "sub_contractor_master_id"),
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivitySubContractorAssignment extends BaseEntity {

  @Column(name = "activity_id", nullable = false)
  private UUID activityId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "sub_contractor_master_id", nullable = false)
  private UUID subContractorMasterId;

  @Column(name = "units", precision = 19, scale = 4)
  private BigDecimal units;
}
