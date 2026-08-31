package com.bipros.ai.match;

import java.util.List;

/**
 * Deterministic 0-100 string similarity used to resolve a spoken/free-text label to a canonical
 * option (a supervisor name, an activity, a unit, a role). Lifted verbatim from
 * {@code EntityResolverTool}'s private scorer so the voice-fill resolver shares the exact same
 * matching semantics.
 *
 * <p>The Levenshtein branch is capped at 74 — below any sensible "accept" threshold — so a pure
 * edit-distance guess never auto-selects on its own; only exact / prefix / substring hits do. That
 * cap is the core false-positive guard for voice-fill dropdown resolution.
 *
 * <p>TODO future cleanup: migrate the three existing copies of this logic
 * ({@code EntityResolverTool}, {@code FindResourceDeploymentTool},
 * {@code CompareResourcesAcrossProjectsTool}) to this shared class.
 */
public final class LabelMatcher {

  /** Below this, a candidate is noise. Mirrors {@code EntityResolverTool.MIN_SCORE}. */
  public static final int MIN_SCORE = 35;

  private LabelMatcher() {}

  /**
   * 0..100 score: 100 = exact (normalized) match, 90 = haystack starts with query, 75 = haystack
   * contains query, 70 = query contains haystack, else a Levenshtein similarity capped at 74.
   */
  public static int score(String query, String haystack) {
    if (query == null || haystack == null) return 0;
    String q = norm(query);
    String h = norm(haystack);
    if (q.isEmpty() || h.isEmpty()) return 0;
    if (q.equals(h)) return 100;
    if (h.startsWith(q)) return 90;
    if (h.contains(q)) return 75;
    if (q.contains(h)) return 70;
    int dist = levenshtein(q, h);
    int max = Math.max(q.length(), h.length());
    int sim = (int) Math.round(100.0 * (1.0 - (double) dist / Math.max(1, max)));
    return Math.max(0, Math.min(74, sim));
  }

  /** Best score of {@code query} against any of a candidate's alias strings (nulls skipped). */
  public static int scoreBest(String query, List<String> aliases) {
    if (aliases == null) return 0;
    int best = 0;
    for (String a : aliases) {
      if (a == null) continue;
      int s = score(query, a);
      if (s > best) best = s;
    }
    return best;
  }

  /** Lowercase, collapse runs of non-alphanumerics to a single space, trim. */
  public static String norm(String s) {
    return s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
  }

  public static int levenshtein(String a, String b) {
    int[] prev = new int[b.length() + 1];
    int[] curr = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) prev[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      curr[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] tmp = prev;
      prev = curr;
      curr = tmp;
    }
    return prev[b.length()];
  }
}
