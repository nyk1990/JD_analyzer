package com.nkharade.jdanalyzer.chunking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(20);

    @Test
    void stripsBulletsAndSkipsHeadingsAndShortLines() {
        String resume = """
                EXPERIENCE
                Senior Software Engineer, Experian
                • Built event-driven microservices with Spring Boot and Kafka on AWS
                - Reduced p99 latency by 40% by adding Redis caching
                Java
                """;

        assertThat(chunker.resumeChunks(resume)).containsExactly(
                "Senior Software Engineer, Experian",
                "Built event-driven microservices with Spring Boot and Kafka on AWS",
                "Reduced p99 latency by 40% by adding Redis caching");
    }

    @Test
    void dropsContactLinesFromResume() {
        String resume = """
                nikhil@example.com | (555) 123-4567 | Schaumburg, IL
                Designed REST APIs consumed by 30+ internal teams
                """;

        assertThat(chunker.resumeChunks(resume)).containsExactly("Designed REST APIs consumed by 30+ internal teams");
    }

    @Test
    void filtersJdBoilerplateAndRespectsCap() {
        String jd = """
                Requirements:
                1. 5+ years of experience building backend services in Java
                2) Hands-on experience with AWS (ECS, S3, SQS)
                * Experience with PostgreSQL and query tuning
                We are an equal opportunity employer and value diversity.
                Benefits include a 401(k) match and paid time off.
                """;

        List<String> reqs = chunker.jobRequirements(jd, 2);

        assertThat(reqs).containsExactly(
                "5+ years of experience building backend services in Java",
                "Hands-on experience with AWS (ECS, S3, SQS)");
    }

    @Test
    void splitsVeryLongParagraphsIntoSentences() {
        String sentence = "You will design and operate distributed systems that process millions of events per day. ";
        String longLine = sentence.repeat(4).trim();

        assertThat(chunker.jobRequirements(longLine, 10))
                .hasSize(1) // identical sentences are de-duplicated
                .first().asString().startsWith("You will design");
    }

    @Test
    void blankInputGivesNoChunks() {
        assertThat(chunker.resumeChunks("   ")).isEmpty();
        assertThat(chunker.jobRequirements(null, 5)).isEmpty();
    }
}
