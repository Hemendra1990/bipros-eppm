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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Material handed back from a custodian against the {@link MaterialIssue} that gave it out —
 * the counterpart document every store ledger needs (SAP MM 262, "SRV / Material Return Note").
 * Until this existed an issue could never be reversed, so anything physically returned stayed
 * charged to the custodian and missing from the store balance.
 *
 * <p>{@code USABLE} goes back on the shelf: it credits {@link MaterialStock} so the quantity can
 * be re-issued, and writes a receipt row into the daily {@link MaterialConsumptionLog} so the
 * store closing balance moves. {@code SCRAP} is written off — it drains the custodian's holding
 * but never returns to stock, which is what keeps it counted as wastage.
 */
@Entity
@Table(
    name = "material_return",
    schema = "resource",
    indexes = {
        @Index(name = "idx_material_return_project", columnList = "project_id"),
        @Index(name = "idx_material_return_issue", columnList = "material_issue_id"),
        @Index(name = "idx_material_return_date", columnList = "return_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialReturn extends BaseEntity {

    /** Physical state of the material coming back. Only USABLE re-enters stock. */
    public enum ReturnCondition { USABLE, SCRAP }

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** The issue slip being reversed — a return always references what was issued. */
    @Column(name = "material_issue_id", nullable = false)
    private UUID materialIssueId;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    /** Column is {@code return_condition}: "condition" is a reserved word in the SQL standard. */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_condition", nullable = false, length = 12)
    private ReturnCondition condition;

    /** Custodian handing it back — copied from the issue slip's {@code issuedToUserId}. */
    @Column(name = "returned_by_user_id")
    private UUID returnedByUserId;

    @Column(name = "received_by_user_id")
    private UUID receivedByUserId;

    @Column(name = "remarks", length = 500)
    private String remarks;
}
