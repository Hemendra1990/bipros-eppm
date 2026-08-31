package com.bipros.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response from the admin unit-consistency repair endpoint: per-phase counts plus the
 * activities/BOQ items that need manual attention and a capped sample of relabels.
 */
public record UnitConsistencyRepairResponse(
    boolean dryRun,
    Summary summary,
    List<UnmappedActivity> unmappedActivities,
    List<BoqConflict> boqConflicts,
    List<Sample> samples
) {
    public record Summary(
        Anchor anchor,
        Dpr dpr,
        Boq boq
    ) {}

    /** ANCHOR phase — WorkActivity.defaultUnit + ProductivityNorm.unit normalized to canonical spelling. */
    public record Anchor(
        int workActivitiesNormalized,
        int normsNormalized
    ) {}

    /** DPR phase counts. */
    public record Dpr(
        int scanned,
        int relabeled,
        int alreadyConsistent,
        int skippedUnmapped,
        int skippedNoActivity
    ) {}

    /** BOQ phase counts. */
    public record Boq(
        int scanned,
        int relabeled,
        int alreadyConsistent,
        int skippedConflict,
        int skippedUnused
    ) {}

    /** An activity with no resolvable canonical unit (no work activity mapped / blank default unit). */
    public record UnmappedActivity(
        UUID activityId,
        String code,
        String name,
        int dprCount
    ) {}

    /** A BOQ item whose linked activities disagree on the canonical unit — skipped, needs manual review. */
    public record BoqConflict(
        UUID boqItemId,
        String itemNo,
        String currentUnit,
        List<String> candidateUnits
    ) {}

    /** A capped sample of an individual relabel, for spot-checking a dry run. */
    public record Sample(
        String kind,   // "DPR" | "BOQ" | "WORK_ACTIVITY" | "NORM"
        String id,
        String from,
        String to
    ) {}
}
