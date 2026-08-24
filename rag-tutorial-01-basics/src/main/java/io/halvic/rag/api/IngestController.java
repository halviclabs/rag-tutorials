package io.halvic.rag.api;

import java.io.IOException;
import java.util.List;

import io.halvic.rag.ingestion.DocumentIngestionService;
import io.halvic.rag.ingestion.IngestedDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class IngestController {

    public record IngestUrlRequest(String url) {
    }

    private final DocumentIngestionService ingestionService;

    public IngestController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest/file")
    public IngestedDocument ingestFile(@RequestParam("file") MultipartFile file) throws IOException {
        return ingestionService.ingestFile(file);
    }

    @PostMapping("/ingest/url")
    public IngestedDocument ingestUrl(@RequestBody IngestUrlRequest request) throws IOException {
        return ingestionService.ingestUrl(request.url());
    }

    @GetMapping("/documents")
    public List<IngestedDocument> documents() {
        return ingestionService.history();
    }
}
