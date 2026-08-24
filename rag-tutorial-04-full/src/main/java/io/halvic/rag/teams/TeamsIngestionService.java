package io.halvic.rag.teams;

import java.util.Map;

import io.halvic.rag.ingestion.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Re-embeds every answered Teams question as a Q&A document, so future
 * questions — whether asked from Teams or the dashboard — can retrieve prior
 * Teams answers. Runs async so the webhook response is never delayed by an
 * embedding call.
 */
@Service
@ConditionalOnProperty(prefix = "rag.teams", name = "hmac-secret")
public class TeamsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TeamsIngestionService.class);

    private final DocumentIngestionService ingestionService;

    public TeamsIngestionService(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Async
    public void ingestQnA(String question, String answer, String askedBy) {
        try {
            String text = "Question (asked in Microsoft Teams): " + question + "\nAnswer: " + answer;
            ingestionService.ingestText(
                    "Teams Q&A: " + abbreviate(question), "teams", "teams", text,
                    Map.of("askedBy", askedBy != null ? askedBy : "unknown"));
        } catch (Exception e) {
            // async: never let ingestion failures surface to the webhook flow
            log.warn("Failed to re-embed Teams Q&A", e);
        }
    }

    private String abbreviate(String question) {
        return question.length() <= 60 ? question : question.substring(0, 60) + "…";
    }
}
