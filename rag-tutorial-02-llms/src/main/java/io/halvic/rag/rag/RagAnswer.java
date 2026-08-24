package io.halvic.rag.rag;

import java.util.List;

/**
 * The generated answer plus the retrieved chunks it was grounded in, so the UI
 * can show *why* the model answered the way it did.
 */
public record RagAnswer(String answer, List<SourceChunk> sources) {

    public record SourceChunk(String source, String snippet, Double score) {
    }
}
