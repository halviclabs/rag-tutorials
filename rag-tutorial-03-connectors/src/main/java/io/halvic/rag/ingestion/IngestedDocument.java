package io.halvic.rag.ingestion;

import java.time.Instant;

/**
 * One entry of the in-memory ingestion history shown in the dashboard.
 */
public record IngestedDocument(
        String id,
        String name,
        String sourceType,
        String source,
        int chunkCount,
        Instant ingestedAt) {
}
