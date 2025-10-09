package com.example.travel.assistant.service;

import com.example.travel.assistant.agent.TravelAssistantAgent;
import org.springframework.stereotype.Service;

@Service
public class AgentService {
    private final TravelAssistantAgent agent;

    public AgentService(TravelAssistantAgent agent) {
        this.agent = agent;
    }

    public String ask(String prompt) {
        return agent.chat(prompt);
    }
}
