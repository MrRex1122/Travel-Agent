package com.example.travel.assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AssistantConfig {

    @Bean
    public WebClient ollamaWebClient(AssistantOllamaProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
    }
}