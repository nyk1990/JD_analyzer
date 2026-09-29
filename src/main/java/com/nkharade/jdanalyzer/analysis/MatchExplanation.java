package com.nkharade.jdanalyzer.analysis;

import java.util.List;

/**
 * Structured explanation returned by Claude. Spring AI turns this record into a JSON schema,
 * asks the model to answer in that shape, and maps the reply back onto the record.
 */
public record MatchExplanation(
        String summary,
        List<String> strengths,
        List<String> gaps,
        List<String> resumeSuggestions
) {
}
