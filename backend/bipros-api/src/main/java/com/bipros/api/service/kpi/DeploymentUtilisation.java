package com.bipros.api.service.kpi;

/** Deployment utilisation = average daily deployed nos ÷ planned headcount, capped at 1.0. */
public final class DeploymentUtilisation {
    private DeploymentUtilisation() {}

    public record Result(int avgDailyNos, double cappedPct, double rawPct, boolean overflow) {}

    public static Result of(int totalNos, long activeDays, int plannedNos) {
        double avgDaily = activeDays > 0 ? (double) totalNos / activeDays : 0d;
        double raw = plannedNos > 0 ? avgDaily / plannedNos : 0d;
        return new Result((int) Math.round(avgDaily), Math.min(raw, 1.0d), raw, raw > 1.0d);
    }
}
