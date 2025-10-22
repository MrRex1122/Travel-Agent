package com.example.travel.assistant.service;

import com.example.travel.assistant.config.AssistantOllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final String OFFLINE_PREFIX = "SYSTEM: You operate fully offline. Use only internal tools and provided context. Prefer tool calls for factual data. Ask only for missing details. Do not suggest external websites or apps. Keep replies short and plain text.\n\nUser: ";

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

        long start = System.currentTimeMillis();
        log.debug("[AssistantService] Ollama /api/generate ask start model={} temp={} len(prompt)={}", props.getModel(), props.getTemperature(), (prompt == null ? 0 : prompt.length()));
        try {
            OllamaGenerateResponse resp = ollama
                    .post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(request))
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .block();
            long dur = System.currentTimeMillis() - start;
            String out = resp != null ? resp.response : null;
            log.debug("[AssistantService] Ollama ask done in {} ms; len(response)={}", dur, out == null ? 0 : out.length());
            return out;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException httpEx) {
            // Some Ollama setups/modes do not support /api/generate for given model and return 4xx/5xx; fall back to /api/chat
            log.warn("[AssistantService] /api/generate returned {}. Falling back to /api/chat", httpEx.getStatusCode().value());
            long chatStart = System.currentTimeMillis();
            Map<String, Object> chatReq = new HashMap<>();
            chatReq.put("model", props.getModel());
            chatReq.put("stream", false);
            Map<String, Object> chatOptions = new HashMap<>();
            chatOptions.put("temperature", props.getTemperature());
            chatReq.put("options", chatOptions);
            java.util.List<Map<String, Object>> messages = new java.util.ArrayList<>();
            messages.add(java.util.Map.of("role", "system", "content", "You operate fully offline. Use only internal tools and provided context. Keep replies short and plain text."));
            messages.add(java.util.Map.of("role", "user", "content", (prompt == null ? "" : prompt)));
            chatReq.put("messages", messages);
            OllamaChatResponse chatResp = ollama
                    .post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(chatReq))
                    .retrieve()
                    .bodyToMono(OllamaChatResponse.class)
                    .block();
            long chatDur = System.currentTimeMillis() - chatStart;
            String out = (chatResp != null && chatResp.message != null) ? chatResp.message.content : null;
            log.debug("[AssistantService] Ollama /api/chat fallback done in {} ms; len(response)={}", chatDur, out == null ? 0 : out.length());
            return out;
        } catch (Exception ex) {
            log.warn("[AssistantService] Ollama ask error: {}", ex.toString());
            return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    public String askJson(String prompt) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", props.getModel());
        // For JSON normalization we pass the prompt as-is to maximize adherence to the schema
        request.put("prompt", prompt == null ? "" : prompt);
        request.put("stream", false);
        request.put("format", "json");
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", props.getTemperature());
        request.put("options", options);

        long start = System.currentTimeMillis();
        log.debug("[AssistantService] Ollama /api/generate askJson start model={} temp={} len(prompt)={}", props.getModel(), props.getTemperature(), (prompt == null ? 0 : prompt.length()));
        try {
            OllamaGenerateResponse resp = ollama
                    .post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(request))
                    .retrieve()
                    .bodyToMono(OllamaGenerateResponse.class)
                    .block();
            long dur = System.currentTimeMillis() - start;
            String out = resp != null ? resp.response : null;
            log.debug("[AssistantService] Ollama askJson done in {} ms; len(response)={}", dur, out == null ? 0 : out.length());
            return out;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException httpEx) {
            log.warn("[AssistantService] /api/generate (json) returned {}. Falling back to /api/chat", httpEx.getStatusCode().value());
            long chatStart = System.currentTimeMillis();
            Map<String, Object> chatReq = new HashMap<>();
            chatReq.put("model", props.getModel());
            chatReq.put("stream", false);
            chatReq.put("format", "json");
            Map<String, Object> chatOptions = new HashMap<>();
            chatOptions.put("temperature", props.getTemperature());
            chatReq.put("options", chatOptions);
            java.util.List<Map<String, Object>> messages = new java.util.ArrayList<>();
            messages.add(java.util.Map.of("role", "user", "content", (prompt == null ? "" : prompt)));
            chatReq.put("messages", messages);
            OllamaChatResponse chatResp = ollama
                    .post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(chatReq))
                    .retrieve()
                    .bodyToMono(OllamaChatResponse.class)
                    .block();
            long chatDur = System.currentTimeMillis() - chatStart;
            String out = (chatResp != null && chatResp.message != null) ? chatResp.message.content : null;
            log.debug("[AssistantService] Ollama /api/chat (json) fallback done in {} ms; len(response)={}", chatDur, out == null ? 0 : out.length());
            return out;
        } catch (Exception ex) {
            log.warn("[AssistantService] Ollama askJson error: {}", ex.toString());
            return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    // Minimal DTO matching Ollama /api/generate response
    public static class OllamaGenerateResponse {
        public String response;
        public OllamaGenerateResponse() {}
        public OllamaGenerateResponse(String response) { this.response = response; }
    }

    // Minimal DTO matching Ollama /api/chat response
    public static class OllamaChatResponse {
        public ChatMessage message;
        public static class ChatMessage {
            public String role;
            public String content;
        }
    }
}