package com.bipros.project.domain.model;

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

import java.util.UUID;

/**
 * Sub-contractor line item under a {@link DailyProgressReport} row. Each row records which
 * sub-contractor worked on that activity that day. {@code dprId} is a soft FK — lifecycle is
 * managed transactionally by {@code DailyProgressReportService} (replace-on-update semantics).
 */
@Entity
@Table(
    name = "dpr_sub_contractor",
    schema = "project",
    indexes = {
        @Index(name = "idx_dpr_sub_contractor_dpr", columnList = "dpr_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DprSubContractor extends BaseEntity {

  @Column(name = "dpr_id", nullable = false)
  private UUID dprId;

  @Column(name = "sub_contractor_master_id")
  private UUID subContractorMasterId;

  @Column(name = "sub_contractor_name", nullable = false, length = 200)
  private String subContractorName;

  @Column(name = "sub_contractor_code", length = 50)
  private String subContractorCode;

  @Column(name = "remarks", length = 500)
  private String remarks;
}
