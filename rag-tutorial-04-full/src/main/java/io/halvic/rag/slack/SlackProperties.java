package io.halvic.rag.slack;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the optional Slack connector. The connector only activates
 * once {@code rag.slack.signing-secret} is set.
 *
 * @param signingSecret the app's Signing Secret (Basic Information page); used
 *                      to verify Slack's request signatures
 * @param botToken      the Bot User OAuth Token ({@code xoxb-...}); used to
 *                      post answers via {@code chat.postMessage}
 */
@ConfigurationProperties(prefix = "rag.slack")
public record SlackProperties(String signingSecret, String botToken) {
}
