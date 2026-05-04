package com.bipros.ai.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side fallback that injects a chart fence into an assistant answer
 * when the prose contains 3+ comparable numeric bullets and the model didn't
 * already emit one. Prompt-only steering proved unreliable for this — some
 * provider/model combinations stubbornly refuse to emit unfamiliar fence
 * languages — so we backstop the contract here, after the model finishes.
 *
 * Heuristic: scan for runs of bullet lines that look like
 *   "- Label: 123 …" / "- **Label:** 123 …" / "- Label — 123 …"
 * Take the longest such run, build a bar-chart spec, and append a fenced
 * ```chart``` block. Idempotent: if the answer already contains a chart
 * fence we leave it alone.
 */
public final class ChartAugmenter {

    private ChartAugmenter() {}

    private static final Pattern CHART_FENCE = Pattern.compile("```chart\\b");

    // Bullet — accept "-", "*", or "1.". Optional bold around the label.
    // Capture label (group 1) and the FIRST integer/decimal on the line (group 2).
    private static final Pattern BULLET_NUMBER = Pattern.compile(
            "^\\s*(?:[-*]|\\d+\\.)\\s+\\*{0,2}([^*:—\\n]{1,40}?)\\*{0,2}\\s*[:—]\\s*\\*{0,2}\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\b"
    );

    public static String augment(String text) {
        if (text == null || text.isBlank()) return text;
        if (CHART_FENCE.matcher(text).find()) return text; // already has a chart

        String[] lines = text.split("\\r?\\n", -1);
        List<int[]> runs = new ArrayList<>();      // [startIdx, endIdxExclusive]
        List<List<double[]>> runValues = new ArrayList<>();
        List<List<String>> runLabels = new ArrayList<>();

        int i = 0;
        while (i < lines.length) {
            Matcher m = BULLET_NUMBER.matcher(lines[i]);
            if (!m.find()) { i++; continue; }
            int start = i;
            List<String> labels = new ArrayList<>();
            List<double[]> values = new ArrayList<>();
            while (i < lines.length) {
                Matcher mm = BULLET_NUMBER.matcher(lines[i]);
                if (!mm.find()) break;
                String label = mm.group(1).trim();
                if (label.isEmpty() || label.length() > 40) break;
                double v;
                try {
                    v = Double.parseDouble(mm.group(2).replace(",", ""));
                } catch (NumberFormatException nfe) {
                    break;
                }
                labels.add(label);
                values.add(new double[]{v});
                i++;
            }
            if (i - start >= 3) {
                runs.add(new int[]{start, i});
                runValues.add(values);
                runLabels.add(labels);
            }
        }

        if (runs.isEmpty()) return text;

        // Pick the longest run; on ties, the first one.
        int best = 0;
        for (int k = 1; k < runs.size(); k++) {
            int kLen = runs.get(k)[1] - runs.get(k)[0];
            int bLen = runs.get(best)[1] - runs.get(best)[0];
            if (kLen > bLen) best = k;
        }

        List<String> labels = runLabels.get(best);
        List<double[]> values = runValues.get(best);

        // Don't chart percentages-only rows (e.g. "16% completed") — they are
        // already a visual breakdown in prose.
        boolean allPercent = labels.stream().allMatch(l -> l.toLowerCase().contains("percent")
                || l.endsWith("%"));
        if (allPercent) return text;

        // Title: derive from a nearby header or fall back to a generic one.
        String title = inferTitle(text, runs.get(best)[0]);
        // Pie if the labels look like parts-of-a-whole (3-6 categories, all
        // positive). Otherwise bar.
        boolean piey = labels.size() >= 3 && labels.size() <= 6
                && values.stream().allMatch(v -> v[0] >= 0)
                && looksPartsOfWhole(labels);
        String type = piey ? "pie" : "bar";

        StringBuilder spec = new StringBuilder();
        spec.append("{\"title\":\"").append(escape(title)).append("\",\"type\":\"")
            .append(type).append("\",\"x\":[");
        for (int k = 0; k < labels.size(); k++) {
            if (k > 0) spec.append(",");
            spec.append("\"").append(escape(labels.get(k))).append("\"");
        }
        spec.append("],\"y\":[");
        for (int k = 0; k < values.size(); k++) {
            if (k > 0) spec.append(",");
            double v = values.get(k)[0];
            if (v == Math.floor(v)) spec.append((long) v);
            else spec.append(v);
        }
        spec.append("]}");

        return text + "\n\n```chart\n" + spec + "\n```";
    }

    private static String inferTitle(String text, int runStartLine) {
        // Walk backward from runStart looking for a markdown heading or a
        // bolded leading line; otherwise fall back to a generic label.
        String[] lines = text.split("\\r?\\n", -1);
        for (int k = runStartLine - 1; k >= 0 && k > runStartLine - 6; k--) {
            String l = lines[k].trim();
            if (l.isEmpty()) continue;
            // Strip markdown decoration to keep the title short and clean.
            String cleaned = l.replaceAll("^#+\\s*", "")
                              .replaceAll("\\*\\*", "")
                              .replaceAll("[`_]", "")
                              .replaceAll("\\s+", " ")
                              .trim();
            if (!cleaned.isEmpty() && cleaned.length() <= 60) {
                // Trim trailing colon / dash punctuation.
                cleaned = cleaned.replaceAll("[:\\-—]+$", "").trim();
                if (cleaned.length() >= 4) return cleaned;
            }
        }
        return "Breakdown";
    }

    private static boolean looksPartsOfWhole(List<String> labels) {
        // A weak signal: labels read like states / categories rather than
        // ranked items (e.g. "Top 3 …").
        for (String l : labels) {
            String low = l.toLowerCase();
            if (low.contains("top ") || low.contains("rank")) return false;
        }
        return true;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
