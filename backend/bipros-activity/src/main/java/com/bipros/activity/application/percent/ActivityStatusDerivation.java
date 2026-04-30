package com.bipros.activity.application.percent;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityStatus;

import java.time.LocalDate;

/**
 * Single source of truth for deriving {@link ActivityStatus} from percent complete
 * and actual dates. Used by {@link PercentCompleteCalculator},
 * {@link com.bipros.activity.application.service.ActivityStepService},
 * and {@link com.bipros.activity.application.service.ActivityService}.
 */
public final class ActivityStatusDerivation {

    private ActivityStatusDerivation() {
        // utility class
    }

    /**
     * Derive status from raw percent and optional actual-start.
     * <ul>
     *   <li>pct ≥ 100 → COMPLETED</li>
     *   <li>pct > 0 OR actualStartDate set → IN_PROGRESS</li>
     *   <li>otherwise → NOT_STARTED</li>
     * </ul>
     */
    public static ActivityStatus derive(Double percentComplete, LocalDate actualStartDate) {
        if (percentComplete != null && percentComplete >= 100.0) {
            return ActivityStatus.COMPLETED;
        }
        if ((percentComplete != null && percentComplete > 0.0) || actualStartDate != null) {
            return ActivityStatus.IN_PROGRESS;
        }
        return ActivityStatus.NOT_STARTED;
    }

    /**
     * Convenience overload that reads {@code percentComplete} and
     * {@code actualStartDate} from the activity entity.
     */
    public static ActivityStatus derive(Activity activity) {
        return derive(activity.getPercentComplete(), activity.getActualStartDate());
    }
}
