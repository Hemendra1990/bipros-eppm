package com.bipros.resource.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One custodian × material × bucket line from {@code MaterialIdleStockService}: how much of what
 * the store issued to a person is now surplus to the work still left on their fronts.
 *
 * <p>{@code excess = holding − need − graceExcluded}. A negative or zero excess means they are
 * holding no more than the remaining work needs. {@code alerting} additionally applies the two
 * tolerance gates (share of issued, and money value) so callers never re-derive them.
 */
public record IdleStockRow(
    UUID custodianUserId,
    String custodianName,
    String materialKey,
    String materialName,
    String unit,
    Bucket bucket,
    /** Set only for {@link Bucket#ACTIVITY} rows — the activity the slips were tagged to. */
    UUID activityId,
    String activityName,
    /** The triggering activity's % complete for ACTIVITY rows; the highest of the pooled
     *  activities for PERSON rows, which is what makes the pool worth checking. */
    BigDecimal percentComplete,
    BigDecimal issuedToDate,
    BigDecimal returnedToDate,
    BigDecimal consumedToDate,
    BigDecimal holding,
    BigDecimal need,
    BigDecimal graceExcluded,
    BigDecimal excess,
    /** Null when no DPR or GRN rate can be resolved — the value gate is then skipped. */
    BigDecimal rate,
    BigDecimal excessValue,
    boolean alerting,
    LocalDate earliestIssueDate,
    List<String> challanNumbers
) {
    public enum Bucket { ACTIVITY, PERSON }
}
