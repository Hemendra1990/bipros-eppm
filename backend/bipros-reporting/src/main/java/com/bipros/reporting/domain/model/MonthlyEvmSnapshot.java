package com.bipros.reporting.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * IC-PMS M9 monthly EVM snapshot — one row per (reportMonth × WBS node). Drives the
 * Monthly Progress Report (MPR) grid and feeds the programme-level rollups.
 */
@Entity
@Table(
    name = "monthly_evm_snapshots",
    schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_monthly_evm_node_month",
            columnNames = {"node_id", "report_month"})
    },
    indexes = {
        @Index(name = "idx_monthly_evm_node", columnList = "node_id"),
        @Index(name = "idx_monthly_evm_month", columnList = "report_month")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyEvmSnapshot extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    /** Denormalised WBS node code for grid display (e.g. DMIC-N03). */
    @Column(name = "node_code", length = 60)
    private String nodeCode;

    /** First day of the report month (e.g. 2025-03-01 for March 2025). */
    @Column(name = "report_month", nullable = false)
    private LocalDate reportMonth;

    // EVM core ---------------------------------------------------------------
    @Column(name = "bcws", precision = 15, scale = 2)
    private BigDecimal bcws;
    @Column(name = "bcwp", precision = 15, scale = 2)
    private BigDecimal bcwp;
    @Column(name = "acwp", precision = 15, scale = 2)
    private BigDecimal acwp;
    @Column(name = "bac", precision = 15, scale = 2)
    private BigDecimal bac;

    @Column(name = "spi", precision = 6, scale = 3)
    private BigDecimal spi;
    @Column(name = "cpi", precision = 6, scale = 3)
    private BigDecimal cpi;

    @Column(name = "eac", precision = 15, scale = 2)
    private BigDecimal eac;
    @Column(name = "etc", precision = 15, scale = 2)
    private BigDecimal etc;
    @Column(name = "cv", precision = 15, scale = 2)
    private BigDecimal cv;
    @Column(name = "sv", precision = 15, scale = 2)
    private BigDecimal sv;

    @Column(name = "pct_complete_ai", precision = 5, scale = 2)
    private BigDecimal pctCompleteAi;
    @Column(name = "pct_complete_contractor", precision = 5, scale = 2)
    private BigDecimal pctCompleteContractor;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_status", length = 30)
    private ScheduleStatus scheduleStatus;

    @Column(name = "red_risks_count")
    private Integer redRisksCount;

    @Column(name = "open_ra_bills_crores", precision = 12, scale = 2)
    private BigDecimal openRaBillsCrores;

    @Column(name = "mpr_status", length = 30)
    private String mprStatus;

    public enum ScheduleStatus {
        ON_SCHEDULE,
        BEHIND_SCHEDULE,
        DELAYED
    }

    /** Band SPI → schedule status: ≥0.95 ON, 0.85-0.95 BEHIND, &lt;0.85 DELAYED. */
    public static ScheduleStatus scheduleStatusFromSpi(BigDecimal spi) {
        if (spi == null) return null;
        double v = spi.doubleValue();
        if (v >= 0.95) return ScheduleStatus.ON_SCHEDULE;
        if (v >= 0.85) return ScheduleStatus.BEHIND_SCHEDULE;
        return ScheduleStatus.DELAYED;
    }
}
