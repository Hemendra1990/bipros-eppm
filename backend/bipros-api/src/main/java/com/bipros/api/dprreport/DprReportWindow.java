package com.bipros.api.dprreport;

import java.time.LocalDate;

public record DprReportWindow(LocalDate from, LocalDate to, String label) {
    public static DprReportWindow ofPreset(DprReportConfig.WindowPreset p, LocalDate today, LocalDate projectStart) {
        return switch (p) {
            case LAST_1_DAY -> new DprReportWindow(today.minusDays(1), today, "Last 1 day");
            case LAST_7_DAYS -> new DprReportWindow(today.minusDays(7), today, "Last 7 days");
            case LAST_30_DAYS -> new DprReportWindow(today.minusDays(30), today, "Last 30 days");
            case THIS_MONTH -> new DprReportWindow(today.withDayOfMonth(1), today, "This month");
            case PROJECT_TO_DATE -> new DprReportWindow(
                projectStart != null ? projectStart : today.minusYears(1), today, "Project to date");
        };
    }
    public static DprReportWindow ofCustom(LocalDate from, LocalDate to) {
        return new DprReportWindow(from, to, from + " to " + to);
    }
}
