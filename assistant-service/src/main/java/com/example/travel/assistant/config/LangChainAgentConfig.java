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
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainAgentConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            AssistantGeminiProperties geminiProps
    ) {
        if (geminiProps.getApiKey() == null || geminiProps.getApiKey().isBlank()) {
            throw new IllegalStateException("assistant.gemini.api-key is required to start assistant-service");
        }
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiProps.getApiKey())
                .modelName(geminiProps.getModel())
                .temperature(geminiProps.getTemperature())
                .timeout(Duration.ofMillis(geminiProps.getRequestTimeoutMs()))
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
                                                     @Value("${assistant.agent.tools-enabled:${ASSISTANT_AGENT_TOOLS_ENABLED:true}}") boolean agentToolsEnabled) {
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
