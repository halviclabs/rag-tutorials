package io.halvic.rag.ingestion.confluence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Minimal Confluence REST API client. Uses the classic
 * {@code /rest/api/content} endpoints, which exist on both Confluence Cloud
 * and Server/Data Center.
 */
@Component
@ConditionalOnProperty(prefix = "rag.confluence", name = "base-url")
public class ConfluenceClient {

    public record ConfluencePage(String id, String title, String bodyHtml, String url) {
    }

    private static final int PAGE_SIZE = 25;

    private final RestClient restClient;
    private final String baseUrl;

    public ConfluenceClient(ConfluenceProperties properties) {
        this.baseUrl = properties.baseUrl().replaceAll("/$", "");
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Authorization", authorizationHeader(properties))
                .build();
    }

    private static String authorizationHeader(ConfluenceProperties props) {
        if (props.personalAccessToken() != null && !props.personalAccessToken().isBlank()) {
            // Server / Data Center: Personal Access Token
            return "Bearer " + props.personalAccessToken();
        }
        if (props.email() != null && props.apiToken() != null) {
            // Cloud: email + API token as Basic auth
            String credentials = props.email() + ":" + props.apiToken();
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalStateException("""
                rag.confluence.base-url is set, but no credentials are configured. Provide either \
                rag.confluence.email + rag.confluence.api-token (Cloud) or \
                rag.confluence.personal-access-token (Server/Data Center).""");
    }

    public ConfluencePage fetchPage(String pageId) {
        JsonNode node = restClient.get()
                .uri("/rest/api/content/{id}?expand=body.storage", pageId)
                .retrieve()
                .body(JsonNode.class);
        return toPage(node);
    }

    public List<ConfluencePage> fetchSpacePages(String spaceKey, int maxPages) {
        List<ConfluencePage> pages = new ArrayList<>();
        int start = 0;
        while (pages.size() < maxPages) {
            JsonNode response = restClient.get()
                    .uri("/rest/api/content?spaceKey={key}&type=page&expand=body.storage&start={start}&limit={limit}",
                            spaceKey, start, Math.min(PAGE_SIZE, maxPages - pages.size()))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode results = response.path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }
            results.forEach(node -> pages.add(toPage(node)));
            if (results.size() < PAGE_SIZE) {
                break;
            }
            start += results.size();
        }
        return pages;
    }

    private ConfluencePage toPage(JsonNode node) {
        String webui = node.path("_links").path("webui").asText("");
        return new ConfluencePage(
                node.path("id").asText(),
                node.path("title").asText("(untitled)"),
                node.path("body").path("storage").path("value").asText(""),
                baseUrl + webui);
    }
}
