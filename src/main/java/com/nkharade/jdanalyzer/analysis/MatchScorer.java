package com.nkharade.jdanalyzer.analysis;

import java.util.List;

/**
 * Turns per-requirement matches into a single 0-100 score.
 *
 * Deliberately simple for v1: the share of JD requirements with resume evidence above the threshold.
 * Easy to explain in an interview, easy to test. Session 4 adds partial credit and
 * required-vs-preferred weighting.
 */
public final class MatchScorer {

    private MatchScorer() {
    }

    public static int score(List<RequirementMatch> matches) {
        if (matches.isEmpty()) return 0;
        long covered = matches.stream().filter(RequirementMatch::covered).count();
        return (int) Math.round(100.0 * covered / matches.size());
    }
}
