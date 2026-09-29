package com.nkharade.jdanalyzer.analysis;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Asks Claude to explain the match. Claude does not compute the score: it reasons over the
 * requirement -> evidence pairs that pgvector already found, which keeps the answer grounded
 * and the prompt small (cheap, fast).
 */
@Component
public class MatchExplainer {

    private static final String SYSTEM_PROMPT = """
            You are a senior technical recruiter reviewing how well a candidate's resume matches a job description.
            You are given each job requirement, the closest resume line found by semantic search, and a similarity score.
            Similarity is computed by a small local embedding model, so treat it as a hint, not the truth:
            a low score can still be a real match with different wording, and a high score can be a false match.

            Rules:
            - Base every statement on the evidence provided. Never invent experience the resume does not show.
            - strengths: 3-6 concrete points where the resume clearly meets the role.
            - gaps: 3-6 requirements that are missing or only weakly evidenced, most important first.
            - resumeSuggestions: up to 5 specific rewrites or additions, only for experience the evidence suggests
              the candidate actually has (for example, rewording a bullet to use the JD's terminology).
            - summary: 2-3 sentences, direct and honest.
            """;

    private final ChatClient chatClient;

    public MatchExplainer(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    public MatchExplanation explain(List<RequirementMatch> matches, int matchScore) {
        return chatClient.prompt()
                .user(buildUserMessage(matches, matchScore))
                .call()
                .entity(MatchExplanation.class);
    }

    static String buildUserMessage(List<RequirementMatch> matches, int matchScore) {
        StringBuilder sb = new StringBuilder()
                .append("Overall coverage score: ").append(matchScore).append("/100\n\n")
                .append("Requirement-by-requirement evidence:\n");
        int i = 1;
        for (RequirementMatch m : matches) {
            sb.append(i++).append(". [").append(m.covered() ? "COVERED" : "WEAK/MISSING").append("] ")
                    .append(safe(m.requirement())).append('\n')
                    .append("   closest resume line: ")
                    .append(m.bestEvidence() == null ? "(none)" : safe(m.bestEvidence()))
                    .append(String.format(Locale.ROOT, "  (similarity %.2f)%n", m.similarity()));
        }
        return sb.toString();
    }

    /**
     * Spring AI runs prompt text through a template engine that treats { } as placeholders.
     * Resume/JD text is user input, so braces are neutralised to keep rendering from failing.
     */
    static String safe(String text) {
        return text.replace('{', '(').replace('}', ')');
    }
}
