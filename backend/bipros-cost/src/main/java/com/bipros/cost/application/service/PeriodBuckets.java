package com.bipros.cost.application.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Generates contiguous calendar buckets covering [windowStart, today]. The first bucket starts at
 *  windowStart; the final bucket's end is clamped to today. Cadence: "D"/"DAILY", "W"/"WEEKLY",
 *  "M"/"MONTHLY" (default monthly). Pure / no I/O. */
public final class PeriodBuckets {

    private PeriodBuckets() {}

    public static List<PeriodBucket> generate(LocalDate windowStart, LocalDate today, String periodType) {
        List<PeriodBucket> out = new ArrayList<>();
        if (windowStart == null || today == null || windowStart.isAfter(today)) return out;
        String c = normalize(periodType);
        LocalDate cursor = windowStart;
        while (!cursor.isAfter(today)) {
            LocalDate naturalEnd = switch (c) {
                case "D" -> cursor;
                case "W" -> cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                default  -> cursor.withDayOfMonth(cursor.lengthOfMonth());
            };
            LocalDate end = naturalEnd.isAfter(today) ? today : naturalEnd;
            out.add(new PeriodBucket(cursor, end, name(cursor, c)));
            cursor = end.plusDays(1);
        }
        return out;
    }

    public static String normalize(String periodType) {
        if (periodType == null) return "M";
        String t = periodType.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("D")) return "D";
        if (t.startsWith("W")) return "W";
        return "M";
    }

    private static String name(LocalDate start, String c) {
        String mon = start.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return switch (c) {
            case "D" -> start.getDayOfMonth() + " " + mon + " " + start.getYear();
            case "W" -> "W" + start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " "
                          + start.get(IsoFields.WEEK_BASED_YEAR);
            default  -> mon + " " + start.getYear();
        };
    }
}
