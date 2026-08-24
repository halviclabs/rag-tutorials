package io.halvic.rag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} for the chat-platform connectors: Slack requires an
 * acknowledgement within 3 seconds, and re-embedding Q&A pairs should never
 * block a webhook response.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
