package io.halvic.rag.ingestion.confluence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.halvic.rag.ingestion.DocumentIngestionService;
import io.halvic.rag.ingestion.IngestedDocument;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Turns Confluence pages into vector store chunks by reusing the shared
 * ingestion pipeline: the page's storage-format HTML is flattened to plain
 * text with Jsoup, then chunked, embedded and stored like any other document.
 */
@Service
@ConditionalOnProperty(prefix = "rag.confluence", name = "base-url")
public class ConfluenceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceIngestionService.class);

    private final ConfluenceClient confluenceClient;
    private final DocumentIngestionService ingestionService;

    public ConfluenceIngestionService(ConfluenceClient confluenceClient,
                                      DocumentIngestionService ingestionService) {
        this.confluenceClient = confluenceClient;
        this.ingestionService = ingestionService;
    }

    public IngestedDocument ingestPage(String pageId) {
        return ingest(confluenceClient.fetchPage(pageId));
    }

    public List<IngestedDocument> ingestSpace(String spaceKey, int maxPages) {
        List<IngestedDocument> ingested = new ArrayList<>();
        for (ConfluenceClient.ConfluencePage page : confluenceClient.fetchSpacePages(spaceKey, maxPages)) {
            try {
                ingested.add(ingest(page));
            } catch (IllegalArgumentException e) {
                log.warn("Skipping Confluence page {} ({}): {}", page.id(), page.title(), e.getMessage());
            }
        }
        return ingested;
    }

    private IngestedDocument ingest(ConfluenceClient.ConfluencePage page) {
        String text = Jsoup.parse(page.bodyHtml()).text();
        return ingestionService.ingestText(
                page.title(), "confluence", page.url(), text,
                Map.of("pageId", page.id(), "title", page.title()));
    }
}
