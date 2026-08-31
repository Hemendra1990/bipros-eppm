package com.bipros.gis.application.port;

import java.util.UUID;

/**
 * Port for the approved-DPR cumulative physical progress % of a WBS node — the contractor's
 * "claimed" progress, compared against satellite-derived progress to compute variance.
 *
 * <p>Implemented by an adapter in a higher module (bipros-ai) that can reach Activity progress, so
 * bipros-gis stays free of a dependency on bipros-activity — a direct {@code gis → activity} edge
 * would close a module cycle (activity → security → project → contract → gis).
 */
public interface WbsProgressProvider {

    /** Cumulative approved-DPR progress % for the WBS node, or {@code null} when it has no activities. */
    Double claimedProgressForWbs(UUID wbsNodeId);
}
