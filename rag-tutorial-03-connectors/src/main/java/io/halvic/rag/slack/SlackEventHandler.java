package io.halvic.rag.slack;

import io.halvic.rag.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Does the real work for a Slack mention <b>after</b> the controller has
 * already acknowledged the event (Slack retries anything not answered within
 * 3 seconds — an LLM call never fits in that budget).
 */
@Component
@ConditionalOnProperty(prefix = "rag.slack", name = "signing-secret")
public class SlackEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackEventHandler.class);

    private final RagService ragService;
    private final SlackClient slackClient;
    private final SlackIngestionService slackIngestionService;

    public SlackEventHandler(RagService ragService, SlackClient slackClient,
                             SlackIngestionService slackIngestionService) {
        this.ragService = ragService;
        this.slackClient = slackClient;
        this.slackIngestionService = slackIngestionService;
    }

    @Async
    public void handle(SlackEventPayload.Event event) {
        String question = stripMention(event.text());
        if (question.isBlank()) {
            slackClient.postMessage(event.channel(),
                    "Ask me something about the ingested documents!", event.ts());
            return;
        }
        try {
            String answer = ragService.ask(question).answer();
            // answer in a thread on the triggering message
            slackClient.postMessage(event.channel(), answer, threadTs(event));
            slackIngestionService.ingestQnA(question, answer, event.user());
        } catch (Exception e) {
            log.warn("Failed to answer Slack question", e);
            slackClient.postMessage(event.channel(),
                    "Sorry, I could not answer that: " + e.getMessage(), threadTs(event));
        }
    }

    private String threadTs(SlackEventPayload.Event event) {
        return event.threadTs() != null ? event.threadTs() : event.ts();
    }

    /** Slack mentions look like {@code <@U0123ABC> the actual question}. */
    private String stripMention(String text) {
        return text == null ? "" : text.replaceAll("<@[^>]+>", "").trim();
    }
}
