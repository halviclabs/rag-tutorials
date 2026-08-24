package io.halvic.rag.ingestion.confluence;

import java.util.List;

import io.halvic.rag.ingestion.IngestedDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Only exists when {@code rag.confluence.base-url} is configured — without it,
 * these endpoints answer 404.
 */
@RestController
@RequestMapping("/api/ingest/confluence")
@ConditionalOnProperty(prefix = "rag.confluence", name = "base-url")
public class ConfluenceIngestController {

    public record PageRequest(String pageId) {
    }

    public record SpaceRequest(String spaceKey, Integer limit) {
    }

    private final ConfluenceIngestionService ingestionService;

    public ConfluenceIngestController(ConfluenceIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/page")
    public IngestedDocument ingestPage(@RequestBody PageRequest request) {
        return ingestionService.ingestPage(request.pageId());
    }

    @PostMapping("/space")
    public List<IngestedDocument> ingestSpace(@RequestBody SpaceRequest request) {
        int limit = request.limit() != null ? request.limit() : 50;
        return ingestionService.ingestSpace(request.spaceKey(), limit);
    }
}
