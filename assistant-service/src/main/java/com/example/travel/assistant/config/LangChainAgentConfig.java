package com.example.travel.assistant.config;

import com.example.travel.assistant.agent.TravelAssistantAgent;
import com.example.travel.assistant.tools.BookingTools;
import com.example.travel.assistant.tools.ProfileLookupTool;
import com.example.travel.assistant.tools.FlightSearchTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainAgentConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${assistant.ollama.base-url:${OLLAMA_BASE_URL:http://localhost:11434}}") String baseUrl,
            @Value("${assistant.ollama.model:${OLLAMA_MODEL:llama3.1}}") String model,
            @Value("${assistant.ollama.request-timeout-ms:${OLLAMA_TIMEOUT_MS:60000}}") long timeoutMs,
            @Value("${assistant.ollama.temperature:${OLLAMA_TEMPERATURE:0.2}}") double temperature
    ) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(Duration.ofMillis(timeoutMs))
                .temperature(temperature)
                .build();
    }

    @Bean
    public TravelAssistantAgent travelAssistantAgent(ChatLanguageModel model, BookingTools bookingTools, ProfileLookupTool profileLookupTool, FlightSearchTool flightSearchTool) {
        return AiServices.builder(TravelAssistantAgent.class)
                .chatLanguageModel(model)
                .tools(bookingTools, profileLookupTool, flightSearchTool)
                .build();
    }
}
