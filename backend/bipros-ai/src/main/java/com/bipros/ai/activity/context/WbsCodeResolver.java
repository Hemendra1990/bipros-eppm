package com.bipros.ai.activity.context;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves an AI-proposed {@code wbsNodeCode} against the existing WBS codes
 * for a project. Three rungs:
 *
 * <ol>
 *   <li><b>Exact</b> — the code is in the existing set as-is.</li>
 *   <li><b>Case-insensitive</b> — the code matches an existing code under
 *       lower-casing. Common when models lower- or upper-case the code.</li>
 *   <li><b>Near-match (Levenshtein ≤ 2)</b> — typos / extra/missing dot.
 *       Unique nearest suggestion only; ties yield no suggestion (we'd rather
 *       say "missing" than guess wrong on ambiguity).</li>
 * </ol>
 *
 * Returns {@link Result} carrying the kind of match and (where applicable) the
 * existing code that should be used.
 */
public final class WbsCodeResolver {

    /** Two-edit distance ceiling — covers most case/punct/typo families. */
    private static final int MAX_DISTANCE = 2;

    private WbsCodeResolver() {}

    public enum Kind { EXACT, CASE_INSENSITIVE, NEAR_MATCH, MISSING }

    public record Result(Kind kind, String resolvedCode) {
        public boolean isResolved() {
            return kind == Kind.EXACT || kind == Kind.CASE_INSENSITIVE;
        }
        public boolean hasSuggestion() {
            return kind == Kind.NEAR_MATCH;
        }
    }

    public static Result resolve(String requested, Set<String> existingCodes) {
        if (requested == null || requested.isBlank() || existingCodes == null || existingCodes.isEmpty()) {
            return new Result(Kind.MISSING, null);
        }
        if (existingCodes.contains(requested)) {
            return new Result(Kind.EXACT, requested);
        }
        // Case-insensitive: cheap O(n) scan; sets are small (≤200 codes).
        String reqLower = requested.toLowerCase(Locale.ROOT);
        for (String c : existingCodes) {
            if (c.toLowerCase(Locale.ROOT).equals(reqLower)) {
                return new Result(Kind.CASE_INSENSITIVE, c);
            }
        }
        // Levenshtein near-match. Track best + second-best to detect ambiguity.
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        int secondDist = Integer.MAX_VALUE;
        for (String c : existingCodes) {
            int d = levenshtein(reqLower, c.toLowerCase(Locale.ROOT), MAX_DISTANCE);
            if (d == Integer.MAX_VALUE) continue;
            if (d < bestDist) {
                secondDist = bestDist;
                bestDist = d;
                best = c;
            } else if (d < secondDist) {
                secondDist = d;
            }
        }
        if (best != null && bestDist <= MAX_DISTANCE && bestDist < secondDist) {
            return new Result(Kind.NEAR_MATCH, best);
        }
        return new Result(Kind.MISSING, null);
    }

    /**
     * Levenshtein distance with early-exit at {@code maxDistance}. Returns
     * {@link Integer#MAX_VALUE} if the distance exceeds the threshold (so
     * callers can short-circuit instead of computing exact distance for
     * obviously-far candidates).
     */
    static int levenshtein(String a, String b, int maxDistance) {
        if (a.equals(b)) return 0;
        int la = a.length(), lb = b.length();
        if (Math.abs(la - lb) > maxDistance) return Integer.MAX_VALUE;
        if (la == 0) return lb <= maxDistance ? lb : Integer.MAX_VALUE;
        if (lb == 0) return la <= maxDistance ? la : Integer.MAX_VALUE;

        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                if (curr[j] < rowMin) rowMin = curr[j];
            }
            // Early exit: if every cell in this row exceeds the cap, we can stop.
            if (rowMin > maxDistance) return Integer.MAX_VALUE;
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        int d = prev[lb];
        return d > maxDistance ? Integer.MAX_VALUE : d;
    }
}
