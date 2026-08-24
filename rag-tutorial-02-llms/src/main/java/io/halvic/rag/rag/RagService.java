package io.halvic.rag.rag;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The three RAG steps, spelled out:
 * <ol>
 *   <li><b>Retrieval</b> — similarity search over the vector store</li>
 *   <li><b>Augmentation</b> — put the retrieved chunks into the prompt</li>
 *   <li><b>Generation</b> — let the chat model answer, grounded in that context</li>
 * </ol>
 */
@Service
public class RagService {

    private static final String SYSTEM_TEMPLATE = """
            You are a helpful assistant. Answer the user's question using ONLY the
            context below. If the context does not contain the answer, say that you
            don't know instead of guessing. Answer in the language of the question.

            Context:
            %s
            """;

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final int topK;

    public RagService(ChatModel chatModel, VectorStore vectorStore,
                      @Value("${rag.retrieval.top-k:4}") int topK) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.topK = topK;
    }

    public RagAnswer ask(String question) {
        // 1. Retrieval
        List<Document> chunks = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .build());

        // 2. Augmentation
        String context = chunks.isEmpty()
                ? "(no documents have been ingested yet)"
                : chunks.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_TEMPLATE.formatted(context)),
                new UserMessage(question)));

        // 3. Generation
        String answer = chatModel.call(prompt).getResult().getOutput().getText();

        List<RagAnswer.SourceChunk> sources = chunks.stream()
                .map(c -> new RagAnswer.SourceChunk(
                        String.valueOf(c.getMetadata().getOrDefault("source", "unknown")),
                        snippet(c.getText()),
                        c.getScore()))
                .toList();
        return new RagAnswer(answer, sources);
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 240 ? text : text.substring(0, 240) + "…";
    }
}
