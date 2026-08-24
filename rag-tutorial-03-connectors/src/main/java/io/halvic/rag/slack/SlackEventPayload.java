package io.halvic.rag.slack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The (heavily trimmed) Events API envelope Slack POSTs to the events
 * endpoint. {@code challenge} is only present for the one-time
 * {@code url_verification} handshake.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlackEventPayload(
        String type,
        String challenge,
        Event event) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            String type,
            String text,
            String user,
            String channel,
            String ts,
            @JsonProperty("thread_ts") String threadTs,
            @JsonProperty("bot_id") String botId) {
    }
}
