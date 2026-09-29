package com.nkharade.jdanalyzer.analysis;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All SQL lives here, including the pgvector similarity query.
 *
 * Vectors are sent as pgvector text literals ('[0.12,-0.03,...]') and cast with CAST(... AS vector),
 * so no extra JDBC type library is needed.
 */
@Repository
public class AnalysisRepository {

    private final JdbcClient jdbc;

    public AnalysisRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ChunkHit(long id, String content, double similarity) {
    }

    public record StoredAnalysis(UUID id, OffsetDateTime createdAt, int matchScore, String explanationJson) {
    }

    public void insertAnalysis(UUID id, String jobDescription, String resume) {
        jdbc.sql("""
                        INSERT INTO analysis (id, job_description, resume, match_score)
                        VALUES (:id, :jd, :resume, 0)
                        """)
                .param("id", id)
                .param("jd", jobDescription)
                .param("resume", resume)
                .update();
    }

    public void insertChunk(UUID analysisId, int position, String content, float[] embedding) {
        jdbc.sql("""
                        INSERT INTO resume_chunk (analysis_id, position, content, embedding)
                        VALUES (:analysisId, :position, :content, CAST(:embedding AS vector))
                        """)
                .param("analysisId", analysisId)
                .param("position", position)
                .param("content", content)
                .param("embedding", toVectorLiteral(embedding))
                .update();
    }

    /**
     * Nearest resume chunk to a requirement embedding, within one analysis.
     * {@code <=>} is pgvector's cosine distance, so similarity = 1 - distance.
     */
    public Optional<ChunkHit> findClosestChunk(UUID analysisId, float[] queryEmbedding) {
        return jdbc.sql("""
                        SELECT id, content, 1 - (embedding <=> CAST(:q AS vector)) AS similarity
                        FROM resume_chunk
                        WHERE analysis_id = :analysisId
                        ORDER BY embedding <=> CAST(:q AS vector)
                        LIMIT 1
                        """)
                .param("q", toVectorLiteral(queryEmbedding))
                .param("analysisId", analysisId)
                .query((rs, n) -> new ChunkHit(rs.getLong("id"), rs.getString("content"), rs.getDouble("similarity")))
                .optional();
    }

    public void insertRequirement(UUID analysisId, int position, String content,
                                  Long bestChunkId, double similarity, boolean covered) {
        jdbc.sql("""
                        INSERT INTO jd_requirement (analysis_id, position, content, best_chunk_id, similarity, covered)
                        VALUES (:analysisId, :position, :content, :bestChunkId, :similarity, :covered)
                        """)
                .param("analysisId", analysisId)
                .param("position", position)
                .param("content", content)
                .param("bestChunkId", bestChunkId)
                .param("similarity", similarity)
                .param("covered", covered)
                .update();
    }

    public void updateScore(UUID id, int matchScore) {
        jdbc.sql("UPDATE analysis SET match_score = :score WHERE id = :id")
                .param("score", matchScore)
                .param("id", id)
                .update();
    }

    public void updateExplanation(UUID id, String explanationJson) {
        jdbc.sql("UPDATE analysis SET explanation = CAST(:explanation AS jsonb) WHERE id = :id")
                .param("explanation", explanationJson)
                .param("id", id)
                .update();
    }

    public Optional<StoredAnalysis> findAnalysis(UUID id) {
        return jdbc.sql("SELECT id, created_at, match_score, explanation::text AS explanation FROM analysis WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new StoredAnalysis(
                        rs.getObject("id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getInt("match_score"),
                        rs.getString("explanation")))
                .optional();
    }

    public List<RequirementMatch> findRequirements(UUID analysisId) {
        return jdbc.sql("""
                        SELECT r.content, c.content AS evidence, r.similarity, r.covered
                        FROM jd_requirement r
                        LEFT JOIN resume_chunk c ON c.id = r.best_chunk_id
                        WHERE r.analysis_id = :analysisId
                        ORDER BY r.position
                        """)
                .param("analysisId", analysisId)
                .query((rs, n) -> new RequirementMatch(
                        rs.getString("content"),
                        rs.getString("evidence"),
                        rs.getDouble("similarity"),
                        rs.getBoolean("covered")))
                .list();
    }

    /** float[] -> '[0.1,0.2,...]', the text form pgvector accepts. */
    static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
