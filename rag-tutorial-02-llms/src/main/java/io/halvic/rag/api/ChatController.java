package io.halvic.rag.api;

import io.halvic.rag.rag.RagAnswer;
import io.halvic.rag.rag.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    public record ChatRequest(String question) {
    }

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/chat")
    public RagAnswer chat(@RequestBody ChatRequest request) {
        return ragService.ask(request.question());
    }
}
