package com.example.travel.assistant.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssistantServiceTest {

    private static final class StubChatModel implements ChatLanguageModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("ok"))
                    .build();
        }

        @Override
        public Response generate(List<ChatMessage> messages) {
            return Response.from(AiMessage.from("ok"));
        }
    }

    @Test
    void ask() {
        AssistantService assistantService = new AssistantService(new StubChatModel());
        String hello = assistantService.ask("hello");
        assertEquals("ok", hello);
    }
}
