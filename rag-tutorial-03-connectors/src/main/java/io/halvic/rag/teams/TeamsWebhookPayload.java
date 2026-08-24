package io.halvic.rag.teams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The (heavily trimmed) Bot-Framework activity Teams POSTs to an outgoing
 * webhook. {@code text} contains the message HTML including the
 * {@code <at>Bot</at>} mention.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamsWebhookPayload(
        String type,
        String id,
        String text,
        From from) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record From(String id, String name) {
    }
}
