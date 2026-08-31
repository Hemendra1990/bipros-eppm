package com.bipros.ai.voice.dpr;

import com.bipros.ai.match.LabelMatcher;

import java.util.List;
import java.util.function.Function;

/**
 * Resolves a spoken/free-text label to exactly one canonical candidate, conservatively. Each
 * candidate is scored by the best match of the query against its alias strings (name, code, …);
 * a result is only {@link Resolved#confident() confident} — i.e. safe to auto-fill a dropdown —
 * when there is a clear unique winner:
 * <ul>
 *   <li>an outright exact match (score 100) with no other exact match, OR</li>
 *   <li>a top score ≥ {@link #ACCEPT_SCORE} that beats the runner-up by ≥ {@link #MARGIN}.</li>
 * </ul>
 * Otherwise the caller leaves the id null and asks a follow-up, so voice-fill never silently
 * selects the wrong supervisor / activity / role. Two similar candidates (e.g. two "Kumar"s, or
 * "Mason Grade I" vs "Grade II") stay ambiguous → follow-up.
 */
final class DprLabelResolver {

  /** Minimum top score to auto-accept. Prefix/substring (90/75) clear it; pure fuzzy (≤74) never does. */
  static final int ACCEPT_SCORE = 75;

  /** Required lead over the runner-up, so two near-equal candidates stay ambiguous. */
  static final int MARGIN = 12;

  private DprLabelResolver() {}

  record Resolved<T>(T best, int score, int runnerUp) {
    boolean confident() {
      if (best == null) return false;
      if (score == 100 && runnerUp < 100) return true; // one clear exact hit
      return score >= ACCEPT_SCORE && (score - runnerUp) >= MARGIN;
    }
  }

  static <T> Resolved<T> resolve(String query, List<T> candidates, Function<T, List<String>> aliasesOf) {
    if (query == null || query.isBlank() || candidates == null || candidates.isEmpty()) {
      return new Resolved<>(null, 0, 0);
    }
    T best = null;
    int bestScore = -1;
    int runnerUp = 0;
    for (T c : candidates) {
      int s = LabelMatcher.scoreBest(query, aliasesOf.apply(c));
      if (s > bestScore) {
        runnerUp = bestScore < 0 ? 0 : bestScore;
        bestScore = s;
        best = c;
      } else if (s > runnerUp) {
        runnerUp = s;
      }
    }
    if (best == null || bestScore < LabelMatcher.MIN_SCORE) {
      return new Resolved<>(null, Math.max(bestScore, 0), runnerUp);
    }
    return new Resolved<>(best, bestScore, runnerUp);
  }
}
