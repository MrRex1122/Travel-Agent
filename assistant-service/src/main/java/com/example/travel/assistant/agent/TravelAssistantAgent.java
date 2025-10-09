package com.example.travel.assistant.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * LangChain4j AI service interface representing the Travel Assistant agent.
 * It can decide to call available tools to fulfill the user's request.
 */
@SystemMessage("You are TravelAssistant, a helpful travel agent. Prefer using available tools to perform actions, " +
        "such as creating a booking or listing bookings. Be concise and return clear answers.")
public interface TravelAssistantAgent {

    /**
     * Chat with the assistant. The agent may invoke tools.
     */
    String chat(String message);
}
