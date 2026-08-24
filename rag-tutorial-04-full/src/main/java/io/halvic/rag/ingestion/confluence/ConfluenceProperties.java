package io.halvic.rag.ingestion.confluence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the optional Confluence connector. The connector only
 * activates once {@code rag.confluence.base-url} is set.
 *
 * <p>Two authentication modes are supported:
 * <ul>
 *   <li><b>Confluence Cloud</b>: {@code email} + {@code api-token}
 *       (Basic auth, token from id.atlassian.com)</li>
 *   <li><b>Server / Data Center</b>: {@code personal-access-token}
 *       (Bearer auth)</li>
 * </ul>
 *
 * @param baseUrl             e.g. {@code https://your-org.atlassian.net/wiki} (Cloud)
 *                            or {@code https://confluence.your-org.com} (Server/DC)
 * @param email               Cloud: the Atlassian account email
 * @param apiToken            Cloud: an API token for that account
 * @param personalAccessToken Server/DC: a Personal Access Token
 */
@ConfigurationProperties(prefix = "rag.confluence")
public record ConfluenceProperties(
        String baseUrl,
        String email,
        String apiToken,
        String personalAccessToken) {
}
