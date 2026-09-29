# JD Analyzer

A Spring Boot service that scores how well a resume matches a job description and explains why.

- **Embeddings run locally** (`all-MiniLM-L6-v2` on ONNX Runtime, tokenized with DJL HuggingFace tokenizers, mean pooling in plain Java): no embedding API key, no per-call cost. The model downloads once (~90 MB) to `~/.jd-analyzer/models`.
- **pgvector** stores resume embeddings and finds the closest resume evidence for each JD requirement.
- **Claude** (Anthropic API via Spring AI) turns the matched evidence into a structured explanation: strengths, gaps and suggested resume edits.

## How it works

```
POST /api/analyses { resume, jobDescription }
        │
        ▼
TextChunker ── resume → bullets/sentences, JD → requirements (headings, contact info, EEO/benefits boilerplate removed)
        │
        ▼
OnnxTextEmbedder (local all-MiniLM-L6-v2, 384-dim vectors, CPU)
        │
        ▼
Postgres + pgvector ── store resume chunks; for each requirement:
        │               ORDER BY embedding <=> requirement_vector LIMIT 1   (cosine distance)
        ▼
MatchScorer ── score = % of requirements whose best evidence clears the similarity threshold
        │
        ▼
Claude ── reasons over requirement → evidence pairs, returns JSON mapped to MatchExplanation
```

If the Claude call fails, the score and per-requirement matches are still saved and returned (`explanationStatus: "UNAVAILABLE"`).

## Run it locally

Prerequisites: JDK 21, Maven 3.9+, Docker, an Anthropic API key.

```bash
# 1. Start Postgres with pgvector
docker compose up -d

# 2. Set your API key (PowerShell: $env:ANTHROPIC_API_KEY="sk-ant-...")
export ANTHROPIC_API_KEY=sk-ant-...

# 3. Run the unit tests, then the app (Flyway creates the schema on startup)
mvn test
mvn spring-boot:run

# 4. Try it
curl -s -X POST http://localhost:8080/api/analyses \
  -H "Content-Type: application/json" \
  -d @samples/request.json | jq
```

The first request is slower: the ONNX runtime and tokenizer warm up. Later requests take a few hundred milliseconds for embeddings plus the Claude call.

Fetch a saved analysis with `GET /api/analyses/{analysisId}`. Health check: `GET /actuator/health`.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `ANTHROPIC_API_KEY` | (required) | Claude API key |
| `ANTHROPIC_MODEL` | `claude-haiku-4-5` | Claude model used for explanations |
| `app.matching.coverage-threshold` | `0.50` | Cosine similarity needed to count a requirement as covered |
| `app.matching.max-requirements` | `40` | Cap on requirements extracted from one JD |
| `app.matching.min-chunk-length` | `20` | Shorter lines are ignored |

## Design decisions

- **Local embeddings instead of a second paid API.** Claude has no embeddings endpoint. A small ONNX model inside the JVM keeps cost at zero and data on the machine. The trade-off is lower semantic quality than large hosted models, which is why the threshold is tunable and Claude is told to treat similarity as a hint.
- **Own embedder instead of Spring AI's TransformersEmbeddingModel.** Spring AI's version pools token vectors with DJL's PyTorch engine, which has no native build for Intel Macs. `OnnxTextEmbedder` runs the same model through ONNX Runtime and does mean pooling + L2 normalisation in ~20 lines of Java, removing PyTorch entirely. DJL tokenizers is pinned to 0.28.0, the last release with an Intel-Mac native library.
- **Requirement-level matching, not one document score.** Embedding whole documents gives one blurry number. Matching each requirement to its best resume line makes the result explainable and gives Claude grounded evidence.
- **Hand-written pgvector SQL (`<=>`, HNSW index) via `JdbcClient`.** Keeps the vector logic visible and testable rather than hidden behind a framework abstraction.
- **LLM call outside the DB transaction.** Slow, external and fallible work doesn't hold a connection or roll back stored results.

## Roadmap

- [ ] Partial-credit scoring and required-vs-preferred weighting
- [ ] Testcontainers integration tests (real pgvector, mocked Claude)
- [ ] Compare one JD against several saved resume versions
- [ ] Deploy to AWS: ECS Fargate + RDS PostgreSQL, API key in Secrets Manager
