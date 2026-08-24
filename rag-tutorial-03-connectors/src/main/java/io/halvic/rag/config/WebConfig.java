package io.halvic.rag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The Angular dev server (localhost:4200) proxies /api to the backend, so CORS
 * is not strictly required in the default setup — this makes the backend also
 * usable without the proxy (e.g. when opening the API from other tools).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST");
    }
}
