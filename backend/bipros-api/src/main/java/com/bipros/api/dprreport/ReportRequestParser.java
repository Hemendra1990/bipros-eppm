package com.bipros.api.dprreport;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Deterministic natural-language request parser for on-demand DPR AI reports.
 *  Handles date-window phrases and email extraction only — filter (supervisor/activity/BOQ)
 *  extraction from free text is a v1.1 enhancement deferred to structured UI controls. */
@Service
public class ReportRequestParser {
    private static final Pattern LAST_N = Pattern.compile("last\\s+(\\d+)\\s+day", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_N_WK = Pattern.compile("last\\s+(\\d+)\\s+week", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");

    /** Returns [fromOffsetDays, toOffsetDays] back from today, or empty. -1 fromOffset is a
     *  sentinel meaning "this month" (from = today.withDayOfMonth(1)). */
    public static Optional<int[]> parseRelativeDays(String text) {
        if (text == null) return Optional.empty();
        String t = text.toLowerCase();
        if (t.contains("yesterday")) return Optional.of(new int[]{1, 0});
        if (t.matches(".*\\btoday\\b.*")) return Optional.of(new int[]{0, 0});
        Matcher wk = LAST_N_WK.matcher(t);
        if (wk.find()) return Optional.of(new int[]{Integer.parseInt(wk.group(1)) * 7, 0});
        Matcher m = LAST_N.matcher(t);
        if (m.find()) return Optional.of(new int[]{Integer.parseInt(m.group(1)), 0});
        if (t.contains("this month")) return Optional.of(new int[]{-1, 0}); // sentinel -1 = month start
        return Optional.empty();
    }

    public static List<String> extractEmails(String text) {
        if (text == null) return List.of();
        List<String> out = new ArrayList<>();
        Matcher m = EMAIL.matcher(text);
        while (m.find()) out.add(m.group());
        return out;
    }

    public ReportRequest parse(UUID projectId, String text, LocalDate today, UUID requestedBy) {
        LocalDate from, to = today;
        String label;
        Optional<int[]> rel = parseRelativeDays(text);
        if (rel.isPresent() && rel.get()[0] == -1) { from = today.withDayOfMonth(1); label = "This month"; }
        else if (rel.isPresent()) { from = today.minusDays(rel.get()[0]); label = "Last " + rel.get()[0] + " day(s)"; }
        else { from = today.minusDays(7); label = "Last 7 days"; }
        List<String> emails = extractEmails(text);
        return new ReportRequest(projectId, from, to, label, null, null, null, "ON_DEMAND", requestedBy, emails, false);
    }
}
