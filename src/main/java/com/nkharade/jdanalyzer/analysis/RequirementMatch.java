package com.nkharade.jdanalyzer.analysis;

/**
 * One JD requirement and the closest resume evidence pgvector found for it.
 *
 * @param bestEvidence resume chunk with the smallest cosine distance, or null if the resume had no chunks
 * @param similarity   cosine similarity (1 - cosine distance), roughly 0..1 for this model
 * @param covered      similarity at or above the configured threshold
 */
public record RequirementMatch(
        String requirement,
        String bestEvidence,
        double similarity,
        boolean covered
) {
}
