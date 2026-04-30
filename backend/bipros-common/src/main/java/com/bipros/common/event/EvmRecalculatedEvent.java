package com.bipros.common.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published by {@code EvmCalculationService} after EVM values are recalculated for a period.
 * Triggers analytics ETL into {@code fact_evm_daily}.
 */
public record EvmRecalculatedEvent(
    UUID projectId,
    UUID evmCalculationId,
    LocalDate dataDate
) {
}
