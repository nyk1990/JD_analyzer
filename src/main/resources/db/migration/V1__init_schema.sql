-- pgvector extension (the pgvector/pgvector Docker image ships it; this enables it in the database)
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE analysis (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    job_description  TEXT          NOT NULL,
    resume           TEXT          NOT NULL,
    match_score      INTEGER       NOT NULL,
    explanation      JSONB
);

-- One row per resume bullet/sentence, with its embedding.
-- 384 = output dimension of all-MiniLM-L6-v2. Changing the embedding model means a new migration + re-embedding.
CREATE TABLE resume_chunk (
    id           BIGSERIAL PRIMARY KEY,
    analysis_id  UUID         NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    position     INTEGER      NOT NULL,
    content      TEXT         NOT NULL,
    embedding    vector(384)  NOT NULL
);

CREATE INDEX idx_resume_chunk_analysis ON resume_chunk (analysis_id);

-- HNSW index for cosine distance (<=>). Pays off once the table holds many resumes;
-- for a single analysis the analysis_id filter does most of the work.
CREATE INDEX idx_resume_chunk_embedding ON resume_chunk USING hnsw (embedding vector_cosine_ops);

-- One row per extracted JD requirement and the best resume evidence found for it.
CREATE TABLE jd_requirement (
    id              BIGSERIAL PRIMARY KEY,
    analysis_id     UUID          NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    position        INTEGER       NOT NULL,
    content         TEXT          NOT NULL,
    best_chunk_id   BIGINT        REFERENCES resume_chunk (id) ON DELETE SET NULL,
    similarity      DOUBLE PRECISION NOT NULL,
    covered         BOOLEAN       NOT NULL
);

CREATE INDEX idx_jd_requirement_analysis ON jd_requirement (analysis_id);
