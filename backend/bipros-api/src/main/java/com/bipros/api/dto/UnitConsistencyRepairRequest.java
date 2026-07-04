package com.bipros.api.dto;

import lombok.Data;

import java.util.List;

/**
 * Request for the admin unit-consistency repair endpoint. dryRun defaults to true (preview only).
 * chunkSize bounds each bulk-update transaction. phases (null/empty = all): ANCHOR, DPR, BOQ.
 */
@Data
public class UnitConsistencyRepairRequest {

    /** Dry-run by default: compute + report, write nothing. */
    private boolean dryRun = true;

    /** Rows per bulk-update chunk / transaction. */
    private int chunkSize = 500;

    /** Optional subset of phases to run; null or empty means all (ANCHOR, DPR, BOQ). */
    private List<String> phases;
}
