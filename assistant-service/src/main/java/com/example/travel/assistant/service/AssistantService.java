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

    private static boolean isMemoryOrCudaError(Throwable ex) {
        if (ex == null) return false;
        String msg = ex.toString();
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("cuda") || m.contains("out of memory") || m.contains("unable to allocate cuda_host buffer")
                || m.contains("error loading model") || m.contains("not enough memory") || m.contains("no cuda device");
    }

    private String askWithModel(String model, String prompt, boolean jsonFormat) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("prompt", jsonFormat ? (prompt == null ? "" : prompt) : (OFFLINE_PREFIX + (prompt == null ? "" : prompt)));
        request.put("stream", false);
        if (jsonFormat) request.put("format", "json");
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", props.getTemperature());
        // Force CPU usage if GPU is not available/desired and shrink context to reduce memory
        options.put("num_ctx", props.getNumCtx());
        options.put("num_gpu", props.getNumGpu());
        request.put("options", options);

        long start = System.currentTimeMillis();
        log.debug("[AssistantService] Ollama /api/generate ask{} start model={} temp={} len(prompt)={}", jsonFormat ? "(json)" : "", model, props.getTemperature(), (prompt == null ? 0 : prompt.length()));
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
            log.debug("[AssistantService] Ollama ask{} done in {} ms; len(response)={}", jsonFormat ? "(json)" : "", dur, out == null ? 0 : out.length());
            return out;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException httpEx) {
            // Some models may not support /api/generate; fall back to /api/chat
            log.warn("[AssistantService] /api/generate returned {} for model {}. Falling back to /api/chat", httpEx.getStatusCode().value(), model);
            long chatStart = System.currentTimeMillis();
            Map<String, Object> chatReq = new HashMap<>();
            chatReq.put("model", model);
            chatReq.put("stream", false);
            if (jsonFormat) chatReq.put("format", "json");
            Map<String, Object> chatOptions = new HashMap<>();
            chatOptions.put("temperature", props.getTemperature());
            chatReq.put("options", chatOptions);
            java.util.List<Map<String, Object>> messages = new java.util.ArrayList<>();
            if (!jsonFormat) {
                messages.add(java.util.Map.of("role", "system", "content", "You operate fully offline. Use only internal tools and provided context. Keep replies short and plain text."));
            }
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
        }
    }

    public String ask(String prompt) {
        try {
            return askWithModel(props.getModel(), prompt, false);
        } catch (Exception ex) {
            log.warn("[AssistantService] Ollama ask error for model {}: {}", props.getModel(), ex.toString());
            if (isMemoryOrCudaError(ex) && !props.isNoFallback()) {
                String fb = props.getFallbackModel();
                log.warn("[AssistantService] Retrying with fallback model {} due to resource error.", fb);
                try { return askWithModel(fb, prompt, false); } catch (Exception ex2) {
                    log.warn("[AssistantService] Fallback model {} also failed: {}", fb, ex2.toString());
                }
            }
            return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    public String askJson(String prompt) {
        try {
            return askWithModel(props.getModel(), prompt, true);
        } catch (Exception ex) {
            log.warn("[AssistantService] Ollama askJson error for model {}: {}", props.getModel(), ex.toString());
            if (isMemoryOrCudaError(ex)) {
                String fb = props.getFallbackModel();
                log.warn("[AssistantService] Retrying (json) with fallback model {} due to resource error.", fb);
                try { return askWithModel(fb, prompt, true); } catch (Exception ex2) {
                    log.warn("[AssistantService] Fallback model (json) {} also failed: {}", fb, ex2.toString());
                }
            }
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