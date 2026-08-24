package io.halvic.rag.ingestion;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jsoup.Jsoup;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * The ingestion pipeline: extract text -> chunk -> embed -> store.
 *
 * <p>Extraction depends on the source (Tika for uploaded files, Jsoup for web
 * pages), everything after that is shared: {@link TokenTextSplitter} cuts the
 * text into chunks, and {@link VectorStore#add(List)} embeds each chunk (via
 * the configured embedding model) and stores it.
 */
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = new TokenTextSplitter();
    private final List<IngestedDocument> history = new CopyOnWriteArrayList<>();

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public IngestedDocument ingestFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        // Tika needs a Resource with a filename to pick the right parser
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        List<Document> extracted = new TikaDocumentReader(resource).get();
        String text = String.join("\n", extracted.stream().map(Document::getText).toList());
        return ingestText(filename, "file", filename, text, Map.of());
    }

    public IngestedDocument ingestUrl(String url) throws IOException {
        org.jsoup.nodes.Document page = Jsoup.connect(url).timeout(10_000).get();
        String title = page.title().isBlank() ? url : page.title();
        return ingestText(title, "url", url, page.body().text(), Map.of("title", title));
    }

    /**
     * Shared tail of the pipeline: chunk -> embed -> store -> record history.
     * Later parts reuse this for other sources (Confluence, Teams, Slack).
     */
    public IngestedDocument ingestText(String name, String sourceType, String source,
                                       String text, Map<String, Object> extraMetadata) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No text could be extracted from " + source);
        }
        Map<String, Object> metadata = new HashMap<>(extraMetadata);
        metadata.put("source", source);
        metadata.put("sourceType", sourceType);

        List<Document> chunks = splitter.apply(List.of(new Document(text, metadata)));
        vectorStore.add(chunks);

        IngestedDocument entry = new IngestedDocument(
                UUID.randomUUID().toString(), name, sourceType, source, chunks.size(), Instant.now());
        history.add(entry);
        return entry;
    }

    public List<IngestedDocument> history() {
        return List.copyOf(history);
    }
}
