package com.nkharade.jdanalyzer.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API response for one analysis.
 *
 * @param explanationStatus "OK", or "UNAVAILABLE" when the Claude call failed. The score and
 *                          requirement matches are still returned, so the endpoint degrades
 *                          gracefully instead of failing outright.
 */
public record AnalysisResult(
        UUID analysisId,
        OffsetDateTime createdAt,
        int matchScore,
        int requirementsTotal,
        int requirementsCovered,
        List<RequirementMatch> requirements,
        MatchExplanation explanation,
        String explanationStatus
) {
}
