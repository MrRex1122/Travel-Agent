package com.example.travel.assistant.service;

import com.example.travel.assistant.config.AssistantOllamaProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class AssistantService {

    private static final String OFFLINE_PREFIX = "SYSTEM: You operate fully offline. You cannot browse the internet and must not suggest searching the web or using external websites or apps. Answer strictly using internal knowledge and the provided context. If you lack the required data, say so briefly and ask for the missing details. Keep answers concise and plain text.\n\nUser: ";

    private final WebClient ollama;
    private final AssistantOllamaProperties props;

    public AssistantService(WebClient ollamaWebClient, AssistantOllamaProperties props) {
        this.ollama = ollamaWebClient;
        this.props = props;
    }

    public String ask(String prompt) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", props.getModel());
        request.put("prompt", OFFLINE_PREFIX + (prompt == null ? "" : prompt));
        request.put("stream", false);
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", props.getTemperature());
        request.put("options", options);

        OllamaGenerateResponse resp = ollama
                .post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .retrieve()
                .bodyToMono(OllamaGenerateResponse.class)
                .onErrorResume(ex -> Mono.just(new OllamaGenerateResponse("Error: " + ex.getMessage())))
                .block();

        return resp != null ? resp.response : null;
    }

    // Minimal DTO matching Ollama /api/generate response
    public static class OllamaGenerateResponse {
        public String response;
        public OllamaGenerateResponse() {}
        public OllamaGenerateResponse(String response) { this.response = response; }
    }
}