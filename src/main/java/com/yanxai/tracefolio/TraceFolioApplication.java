package com.yanxai.tracefolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(excludeName = "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration")
@ConfigurationPropertiesScan
public class TraceFolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceFolioApplication.class, args);
    }
}
