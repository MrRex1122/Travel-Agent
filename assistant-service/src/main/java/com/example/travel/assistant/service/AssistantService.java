package com.example.travel.assistant.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final String OFFLINE_SYSTEM = "You operate fully offline. Use only internal tools and provided context. Prefer tool calls for factual data. Ask only for missing details. Do not suggest external websites or apps. Keep replies short and plain text.";

    private final ChatLanguageModel model;

    public AssistantService(ChatLanguageModel model) {
        this.model = model;
    }

    private String askWithModel(String prompt, boolean jsonFormat) {
        String safePrompt = prompt == null ? "" : prompt;
        long start = System.currentTimeMillis();
        try {
            ChatRequest.Builder builder = ChatRequest.builder();
            if (jsonFormat) {
                builder.responseFormat(ResponseFormat.JSON)
                        .messages(List.of(UserMessage.from(safePrompt)));
            } else {
                builder.messages(List.of(
                        SystemMessage.from(OFFLINE_SYSTEM),
                        UserMessage.from(safePrompt)
                ));
            }
            ChatResponse resp = model.chat(builder.build());
            long dur = System.currentTimeMillis() - start;
            String out = resp != null && resp.aiMessage() != null ? resp.aiMessage().text() : null;
            log.debug("[AssistantService] LLM ask{} done in {} ms; len(response)={}", jsonFormat ? "(json)" : "", dur, out == null ? 0 : out.length());
            return out;
        } catch (Exception ex) {
            log.warn("[AssistantService] LLM ask{} error: {}", jsonFormat ? "(json)" : "", ex.toString());
            if (jsonFormat) {
                try {
                    return model.generate(safePrompt);
                } catch (Exception ex2) {
                    log.warn("[AssistantService] LLM ask(json) fallback error: {}", ex2.toString());
                }
            }
            return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    public String ask(String prompt) {
        return askWithModel(prompt, false);
    }

    public String askJson(String prompt) {
        return askWithModel(prompt, true);
    }
}
