package io.halvic.rag.slack;

import java.util.Map;

import io.halvic.rag.ingestion.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Mirror of the Teams ingestion service: every answered Slack question is
 * re-embedded as a Q&A document so future questions (from Slack or the
 * dashboard) can retrieve prior Slack answers. Async — never blocks the
 * event acknowledgement.
 */
@Service
@ConditionalOnProperty(prefix = "rag.slack", name = "signing-secret")
public class SlackIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SlackIngestionService.class);

    private final DocumentIngestionService ingestionService;

    public SlackIngestionService(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Async
    public void ingestQnA(String question, String answer, String userId) {
        try {
            String text = "Question (asked in Slack): " + question + "\nAnswer: " + answer;
            ingestionService.ingestText(
                    "Slack Q&A: " + abbreviate(question), "slack", "slack", text,
                    Map.of("askedBy", userId != null ? userId : "unknown"));
        } catch (Exception e) {
            log.warn("Failed to re-embed Slack Q&A", e);
        }
    }

    private String abbreviate(String question) {
        return question.length() <= 60 ? question : question.substring(0, 60) + "…";
    }
}
