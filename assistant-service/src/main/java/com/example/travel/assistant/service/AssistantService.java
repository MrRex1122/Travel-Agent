package com.example.travel.assistant.service;

import com.example.travel.assistant.config.AssistantLlmProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final ChatLanguageModel chatLanguageModel;
    private final AssistantLlmProperties llmProperties;

    public AssistantService(ChatLanguageModel chatLanguageModel, AssistantLlmProperties llmProperties) {
        this.chatLanguageModel = chatLanguageModel;
        this.llmProperties = llmProperties;
    }

    private String askWithModel(String prompt, boolean jsonFormat) {
        List<ChatMessage> messages = new ArrayList<>();
        String userPrompt = prompt == null ? "" : prompt;
        if (!jsonFormat) {
            messages.add(SystemMessage.from("You operate fully offline. Use only internal tools and provided context. Prefer tool calls for factual data. Ask only for missing details. Do not suggest external websites or apps. Keep replies short and plain text."));
        }
        messages.add(UserMessage.from(userPrompt));
        long start = System.currentTimeMillis();
        log.debug("[AssistantService] LLM ask{} start provider={} len(prompt)={}", jsonFormat ? "(json)" : "", llmProperties.getProvider(), userPrompt.length());
        ChatResponse response = chatLanguageModel.generate(messages);
        long dur = System.currentTimeMillis() - start;
        String out = response == null || response.aiMessage() == null ? null : response.aiMessage().text();
        log.debug("[AssistantService] LLM ask{} done in {} ms; len(response)={}", jsonFormat ? "(json)" : "", dur, out == null ? 0 : out.length());
        return out;
    }

    public String ask(String prompt) {
        try {
            return askWithModel(prompt, false);
        } catch (Exception ex) {
            log.warn("[AssistantService] LLM ask error for provider {}: {}", llmProperties.getProvider(), ex.toString());
            return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    public String askJson(String prompt) {
        try {
            return askWithModel(prompt, true);
        } catch (Exception ex) {
            log.warn("[AssistantService] LLM askJson error for provider {}: {}", llmProperties.getProvider(), ex.toString());
            return "Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }
}
