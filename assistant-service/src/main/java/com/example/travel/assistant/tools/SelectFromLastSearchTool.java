package com.example.travel.assistant.tools;

import com.example.travel.assistant.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tool that allows the agent to select a flight from the last search results
 * using natural criteria like ordinal/cheapest/earliest/latest/date/destination.
 * Returns a single flight JSON object as text, or a short message if unavailable.
 */
@Component
public class SelectFromLastSearchTool {

    private final AgentService agentService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SelectFromLastSearchTool(AgentService agentService) {
        this.agentService = agentService;
    }

    @Tool("Select a flight from the last search results using optional criteria: ordinal (1-based), cheapest, earliest, latest, date (YYYY-MM-DD), destination (IATA). Returns a JSON object as text or a short message if nothing matches.")
    public String selectFromLast(@MemoryId String memoryId,
                                 Integer ordinal,
                                 Boolean cheapest,
                                 Boolean earliest,
                                 Boolean latest,
                                 String date,
                                 String destination) {
        try {
            AgentService.SelectionCriteria c = new AgentService.SelectionCriteria();
            c.ordinal = ordinal;
            c.cheapest = cheapest;
            c.earliest = earliest;
            c.latest = latest;
            c.date = date;
            c.destination = destination;
            Map<String, Object> chosen = agentService.selectFromLast(memoryId, c);
            if (chosen == null) {
                return "No matching flight found in the last search (or no last search available).";
            }
            agentService.rememberChosen(memoryId, chosen);
            return mapper.writeValueAsString(chosen);
        } catch (Exception e) {
            return "Failed to select from last search: " + e.getMessage();
        }
    }
}
