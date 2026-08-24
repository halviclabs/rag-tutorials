package io.halvic.rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two mutually exclusive vector store beans, selected by Spring profile:
 *
 * <ul>
 *   <li>{@code simple} — the in-memory {@link SimpleVectorStore} from Part 1:
 *       nothing to install, but lost on restart.</li>
 *   <li>{@code pgvector} — a persistent {@link PgVectorStore} backed by the
 *       dockerized PostgreSQL with the pgvector extension.</li>
 * </ul>
 *
 * <p>{@code rag.vector-store.dimensions} must match the embedding model of the
 * active provider profile (1536 for OpenAI/Azure {@code text-embedding-3-small},
 * 768 for Ollama {@code nomic-embed-text}) — pgvector needs it to create its
 * table schema.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    @Profile("simple")
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    @Profile("pgvector")
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
                                     @Value("${rag.vector-store.dimensions}") int dimensions) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .initializeSchema(true)
                .build();
    }
}
