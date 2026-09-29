package com.nkharade.jdanalyzer.analysis;

import com.nkharade.jdanalyzer.chunking.TextChunker;
import com.nkharade.jdanalyzer.config.MatchingProperties;
import com.nkharade.jdanalyzer.embedding.TextEmbedder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The pipeline: chunk -> embed locally -> store in pgvector -> nearest-neighbour per requirement
 * -> score -> Claude explanation.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final TextEmbedder embedder;
    private final AnalysisRepository repository;
    private final MatchExplainer explainer;
    private final MatchingProperties props;
    private final TransactionTemplate tx;
    private final JsonMapper jsonMapper;
    private final TextChunker chunker;

    public AnalysisService(TextEmbedder embedder,
                           AnalysisRepository repository,
                           MatchExplainer explainer,
                           MatchingProperties props,
                           TransactionTemplate tx,
                           JsonMapper jsonMapper) {
        this.embedder = embedder;
        this.repository = repository;
        this.explainer = explainer;
        this.props = props;
        this.tx = tx;
        this.jsonMapper = jsonMapper;
        this.chunker = new TextChunker(props.minChunkLength());
    }

    public AnalysisResult analyze(String resume, String jobDescription) {
        List<String> resumeChunks = chunker.resumeChunks(resume);
        List<String> requirements = chunker.jobRequirements(jobDescription, props.maxRequirements());
        if (resumeChunks.isEmpty()) {
            throw new IllegalArgumentException("No usable lines found in the resume text.");
        }
        if (requirements.isEmpty()) {
            throw new IllegalArgumentException("No usable requirements found in the job description text.");
        }

        // Embedding runs on the CPU with the local ONNX model; do it before opening a DB transaction.
        long t0 = System.currentTimeMillis();
        List<float[]> chunkVectors = embedder.embed(resumeChunks);
        List<float[]> requirementVectors = embedder.embed(requirements);
        log.debug("Embedded {} resume chunks and {} requirements in {} ms",
                resumeChunks.size(), requirements.size(), System.currentTimeMillis() - t0);

        UUID id = UUID.randomUUID();
        List<RequirementMatch> matches = tx.execute(status ->
                storeAndMatch(id, resume, jobDescription, resumeChunks, chunkVectors, requirements, requirementVectors));

        int score = MatchScorer.score(matches);
        repository.updateScore(id, score);

        // The LLM call happens outside the transaction: it is slow and can fail independently.
        explain(id, matches, score);

        return get(id).orElseThrow();
    }

    public Optional<AnalysisResult> get(UUID id) {
        return repository.findAnalysis(id).map(stored -> {
            List<RequirementMatch> reqs = repository.findRequirements(id);
            MatchExplanation explanation = stored.explanationJson() == null
                    ? null
                    : jsonMapper.readValue(stored.explanationJson(), MatchExplanation.class);
            int covered = (int) reqs.stream().filter(RequirementMatch::covered).count();
            return new AnalysisResult(
                    stored.id(), stored.createdAt(), stored.matchScore(),
                    reqs.size(), covered, reqs, explanation,
                    explanation == null ? "UNAVAILABLE" : "OK");
        });
    }

    private List<RequirementMatch> storeAndMatch(UUID id, String resume, String jobDescription,
                                                 List<String> chunks, List<float[]> chunkVectors,
                                                 List<String> requirements, List<float[]> requirementVectors) {
        repository.insertAnalysis(id, jobDescription, resume);
        for (int i = 0; i < chunks.size(); i++) {
            repository.insertChunk(id, i, chunks.get(i), chunkVectors.get(i));
        }

        List<RequirementMatch> matches = new ArrayList<>(requirements.size());
        for (int i = 0; i < requirements.size(); i++) {
            String requirement = requirements.get(i);
            Optional<AnalysisRepository.ChunkHit> hit = repository.findClosestChunk(id, requirementVectors.get(i));
            double similarity = hit.map(AnalysisRepository.ChunkHit::similarity).orElse(0.0);
            boolean covered = similarity >= props.coverageThreshold();

            repository.insertRequirement(id, i, requirement,
                    hit.map(AnalysisRepository.ChunkHit::id).orElse(null), similarity, covered);
            matches.add(new RequirementMatch(requirement,
                    hit.map(AnalysisRepository.ChunkHit::content).orElse(null), similarity, covered));
        }
        return matches;
    }

    private void explain(UUID id, List<RequirementMatch> matches, int score) {
        try {
            long t0 = System.currentTimeMillis();
            MatchExplanation explanation = explainer.explain(matches, score);
            log.debug("Claude explanation took {} ms", System.currentTimeMillis() - t0);
            repository.updateExplanation(id, jsonMapper.writeValueAsString(explanation));
        } catch (RuntimeException e) {
            // Score and matches are already stored; the explanation is best-effort.
            log.warn("Could not generate explanation for analysis {}: {}", id, e.toString());
        }
    }
}