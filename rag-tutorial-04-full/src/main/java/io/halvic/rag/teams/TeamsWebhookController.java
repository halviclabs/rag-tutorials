package io.halvic.rag.teams;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.halvic.rag.rag.RagService;
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
 * Endpoint for a Microsoft Teams <i>outgoing webhook</i>. Teams POSTs the
 * message here whenever the webhook's name is @-mentioned in its channel, and
 * expects a synchronous JSON reply ({@code {"type":"message","text":...}}).
 *
 * <p>Note: Teams can only call a public HTTPS endpoint — use e.g. {@code ngrok}
 * for local testing. Only exists when {@code rag.teams.hmac-secret} is set.
 */
@RestController
@ConditionalOnProperty(prefix = "rag.teams", name = "hmac-secret")
public class TeamsWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookController.class);

    private final TeamsSignatureVerifier signatureVerifier;
    private final TeamsIngestionService teamsIngestionService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    public TeamsWebhookController(TeamsSignatureVerifier signatureVerifier,
                                  TeamsIngestionService teamsIngestionService,
                                  RagService ragService,
                                  ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.teamsIngestionService = teamsIngestionService;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/teams/webhook")
    public ResponseEntity<Map<String, String>> onMessage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String rawBody) throws Exception {

        // the signature covers the raw bytes, so verify before parsing
        if (!signatureVerifier.verify(authorization, rawBody.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected Teams webhook call with invalid HMAC signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("type", "message", "text", "Signature verification failed."));
        }

        TeamsWebhookPayload payload = objectMapper.readValue(rawBody, TeamsWebhookPayload.class);
        String question = stripMention(payload.text());
        if (question.isBlank()) {
            return ResponseEntity.ok(Map.of("type", "message", "text",
                    "Ask me something about the ingested documents!"));
        }

        String answer = ragService.ask(question).answer();
        String askedBy = payload.from() != null ? payload.from().name() : null;
        teamsIngestionService.ingestQnA(question, answer, askedBy);

        return ResponseEntity.ok(Map.of("type", "message", "text", answer));
    }

    /** Teams sends HTML like {@code <at>BotName</at> the actual question}. */
    private String stripMention(String text) {
        if (text == null) {
            return "";
        }
        return org.jsoup.Jsoup.parse(text.replaceAll("<at>.*?</at>", "")).text().trim();
    }
}
