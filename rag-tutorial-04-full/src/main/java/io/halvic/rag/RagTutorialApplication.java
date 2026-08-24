package io.halvic.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagTutorialApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagTutorialApplication.class, args);
    }
}
