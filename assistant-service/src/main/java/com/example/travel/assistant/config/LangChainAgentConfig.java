package com.example.travel.assistant.config;

import com.example.travel.assistant.agent.TravelAssistantAgent;
import com.example.travel.assistant.memory.SharedChatMemoryProvider;
import com.example.travel.assistant.tools.BookingTools;
import com.example.travel.assistant.tools.ProfileLookupTool;
import com.example.travel.assistant.tools.FlightSearchTool;
import com.example.travel.assistant.tools.SelectFromLastSearchTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Locale;

@Configuration
public class LangChainAgentConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(AssistantLlmProperties llmProperties,
                                               AssistantOllamaProperties ollamaProperties,
                                               AssistantGeminiProperties geminiProperties) {
        String provider = llmProperties.getProvider();
        String normalized = provider == null ? "ollama" : provider.toLowerCase(Locale.ROOT);
        if ("gemini".equals(normalized)) {
            String apiKey = geminiProperties.getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("assistant.gemini.api-key is required when provider=gemini");
            }
            return GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(geminiProperties.getModel())
                    .temperature(geminiProperties.getTemperature())
                    .timeout(Duration.ofMillis(geminiProperties.getRequestTimeoutMs()))
                    .build();
        }
        return OllamaChatModel.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .modelName(ollamaProperties.getModel())
                .timeout(Duration.ofMillis(ollamaProperties.getRequestTimeoutMs()))
                .temperature(ollamaProperties.getTemperature())
                .numCtx(ollamaProperties.getNumCtx())
                .build();
    }

    @Bean
    public SharedChatMemoryProvider sharedChatMemoryProvider() {
        return new SharedChatMemoryProvider(50);
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(SharedChatMemoryProvider registry) {
        return registry::get;
    }

    @Bean
    public TravelAssistantAgent travelAssistantAgent(ChatLanguageModel model,
                                                     BookingTools bookingTools,
                                                     ProfileLookupTool profileLookupTool,
                                                     FlightSearchTool flightSearchTool,
                                                     ObjectProvider<SelectFromLastSearchTool> selectFromLastSearchToolProvider,
                                                     ChatMemoryProvider memoryProvider,
                                                     @org.springframework.beans.factory.annotation.Value("${assistant.agent.tools-enabled:${ASSISTANT_AGENT_TOOLS_ENABLED:true}}") boolean agentToolsEnabled) {
        var builder = AiServices.builder(TravelAssistantAgent.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryProvider);
        if (agentToolsEnabled) {
            builder.tools(bookingTools, profileLookupTool, flightSearchTool);
            SelectFromLastSearchTool selector = selectFromLastSearchToolProvider.getIfAvailable();
            if (selector != null) {
                builder.tools(selector);
            }
        }
        return builder.build();
    }
}
