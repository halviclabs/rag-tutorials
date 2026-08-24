package io.halvic.rag.slack;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around Slack's Web API {@code chat.postMessage}, authenticated
 * with the bot token. Unlike Teams (which takes a synchronous reply), Slack
 * answers are posted as separate API calls after the event was acknowledged.
 */
@Component
@ConditionalOnProperty(prefix = "rag.slack", name = "signing-secret")
public class SlackClient {

    private static final Logger log = LoggerFactory.getLogger(SlackClient.class);

    private final RestClient restClient;

    public SlackClient(SlackProperties properties) {
        if (properties.botToken() == null || properties.botToken().isBlank()) {
            throw new IllegalStateException(
                    "rag.slack.signing-secret is set, but rag.slack.bot-token is missing — "
                            + "the bot cannot post answers without it.");
        }
        this.restClient = RestClient.builder()
                .baseUrl("https://slack.com/api")
                .defaultHeader("Authorization", "Bearer " + properties.botToken())
                .build();
    }

    public void postMessage(String channel, String text, String threadTs) {
        Map<String, Object> body = new HashMap<>();
        body.put("channel", channel);
        body.put("text", text);
        if (threadTs != null) {
            body.put("thread_ts", threadTs);
        }
        JsonNode response = restClient.post()
                .uri("/chat.postMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("ok").asBoolean(false)) {
            log.warn("chat.postMessage failed: {}", response != null ? response.path("error").asText() : "no response");
        }
    }
}
