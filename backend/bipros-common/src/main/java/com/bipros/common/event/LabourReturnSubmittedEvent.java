package com.bipros.common.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a labour return is created. Triggers analytics ETL into {@code fact_labour_daily}
 * with {@code source='LABOUR_RETURN'}.
 */
public record LabourReturnSubmittedEvent(
        UUID projectId,
        UUID labourReturnId,
        LocalDate returnDate
) {
}
