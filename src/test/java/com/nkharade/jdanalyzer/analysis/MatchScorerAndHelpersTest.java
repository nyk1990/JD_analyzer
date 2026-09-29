package com.nkharade.jdanalyzer.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScorerAndHelpersTest {

    @Test
    void scoreIsShareOfCoveredRequirements() {
        List<RequirementMatch> matches = List.of(
                new RequirementMatch("Java", "Built services in Java", 0.8, true),
                new RequirementMatch("Kafka", "Used Kafka", 0.7, true),
                new RequirementMatch("Kubernetes", null, 0.2, false));

        assertThat(MatchScorer.score(matches)).isEqualTo(67);
        assertThat(MatchScorer.score(List.of())).isZero();
    }

    @Test
    void vectorLiteralUsesPgvectorTextFormat() {
        assertThat(AnalysisRepository.toVectorLiteral(new float[]{0.5f, -1.0f, 0.25f}))
                .isEqualTo("[0.5,-1.0,0.25]");
    }

    @Test
    void promptNeutralisesTemplateBraces() {
        String msg = MatchExplainer.buildUserMessage(
                List.of(new RequirementMatch("Experience with ${config} files", null, 0.1, false)), 0);

        assertThat(msg).doesNotContain("{").doesNotContain("}").contains("WEAK/MISSING").contains("(none)");
    }
}
