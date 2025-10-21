package com.example.travel.assistant.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI service interface representing the Travel Assistant agent.
 * It can decide to call available tools to fulfill the user's request.
 */
@SystemMessage("You are TravelAssistant, a helpful travel agent. You have access to tools for: flight search (use FlightSearchTool for searching and finding the cheapest flight), bookings (CRUD), and profile lookup. HARD RULES: (1) Never suggest searching the internet or using external websites, apps, or APIs; you have NO internet access. (2) Use only the provided tools and conversation memory to answer. (3) If information is unavailable through tools, ask for missing details or say you do not have that data; do not fabricate and do not mention the web. Keep answers concise, actionable, and plain text. Always leverage conversation memory to stay contextual across turns.")
public interface TravelAssistantAgent {

    /**
     * Chat with the assistant. The agent may invoke tools.
     * The first parameter is a memory id to maintain a per-session conversation memory.
     */
    String chat(@MemoryId String memoryId, @UserMessage String message);
}
