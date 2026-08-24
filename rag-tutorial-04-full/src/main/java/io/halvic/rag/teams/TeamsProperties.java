package io.halvic.rag.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the optional Microsoft Teams connector. The connector only
 * activates once {@code rag.teams.hmac-secret} is set.
 *
 * @param hmacSecret the Base64 "security token" Teams shows when creating the
 *                   outgoing webhook; used to verify the HMAC-SHA256 signature
 *                   of incoming requests
 */
@ConfigurationProperties(prefix = "rag.teams")
public record TeamsProperties(String hmacSecret) {
}
