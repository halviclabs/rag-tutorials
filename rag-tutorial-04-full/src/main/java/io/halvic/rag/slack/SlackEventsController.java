package io.halvic.rag.slack;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint for Slack's Events API. Two hard requirements from Slack:
 * <ul>
 *   <li>answer the one-time {@code url_verification} handshake with the
 *       {@code challenge} value, and</li>
 *   <li>acknowledge every event within <b>3 seconds</b> — so the RAG call
 *       happens asynchronously in {@link SlackEventHandler} and the answer is
 *       posted later via {@code chat.postMessage}.</li>
 * </ul>
 *
 * <p>Only exists when {@code rag.slack.signing-secret} is set.
 */
@RestController
@ConditionalOnProperty(prefix = "rag.slack", name = "signing-secret")
public class SlackEventsController {

    private static final Logger log = LoggerFactory.getLogger(SlackEventsController.class);

    private final SlackSignatureVerifier signatureVerifier;
    private final SlackEventHandler eventHandler;
    private final ObjectMapper objectMapper;

    public SlackEventsController(SlackSignatureVerifier signatureVerifier,
                                 SlackEventHandler eventHandler,
                                 ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.eventHandler = eventHandler;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/slack/events")
    public ResponseEntity<?> onEvent(
            @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Slack-Signature", required = false) String signature,
            @RequestBody String rawBody) throws Exception {

        // the signature covers the raw body string, so verify before parsing
        if (!signatureVerifier.verify(timestamp, signature, rawBody)) {
            log.warn("Rejected Slack event with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SlackEventPayload payload = objectMapper.readValue(rawBody, SlackEventPayload.class);

        // one-time handshake when the request URL is saved in the Slack app config
        if ("url_verification".equals(payload.type())) {
            return ResponseEntity.ok(Map.of("challenge", payload.challenge()));
        }

        if ("event_callback".equals(payload.type()) && payload.event() != null) {
            SlackEventPayload.Event event = payload.event();
            boolean fromBot = event.botId() != null; // never react to bot posts (loop protection)
            if (!fromBot && "app_mention".equals(event.type())) {
                eventHandler.handle(event); // @Async — returns immediately
            }
        }

        // acknowledge within Slack's 3-second budget; the answer comes later
        return ResponseEntity.ok().build();
    }
}
