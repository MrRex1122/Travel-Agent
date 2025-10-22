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

    private String wrapOk(Object data) {
        try {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("status", "OK");
            out.put("data", data);
            return mapper.writeValueAsString(out);
        } catch (Exception e) { return "{\"status\":\"OK\"}"; }
    }

    private String wrapError(String code, String message) {
        try {
            java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
            if (code != null) err.put("code", code);
            if (message != null) err.put("message", message);
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("status", "ERROR");
            out.put("error", err);
            return mapper.writeValueAsString(out);
        } catch (Exception e) { return "{\"status\":\"ERROR\"}"; }
    }

    private static String buildTripId(java.util.Map<String, Object> flight) {
        if (flight == null) return null;
        String carrier = String.valueOf(flight.getOrDefault("carrier", "")).replace(" ", "");
        String num = String.valueOf(flight.getOrDefault("flightNumber", ""));
        String dateVal = String.valueOf(flight.getOrDefault("date", ""));
        if (carrier.isBlank() || num.isBlank() || dateVal.isBlank()) return null;
        return carrier + "-" + num + "-" + dateVal;
    }

    @Tool("Select a flight from the last search results using optional criteria: ordinal (1-based), cheapest, earliest, latest, date (YYYY-MM-DD), destination (IATA), maxPrice, carrier, timeRange (morning/evening), nonstop. Always use this tool to pick from previously listed results instead of parsing free text. Returns {status,data:{selected,ordinal,tripId}} or {status:'ERROR',error}.")
    public String selectFromLast(@MemoryId String memoryId,
                                 Integer ordinal,
                                 Boolean cheapest,
                                 Boolean earliest,
                                 Boolean latest,
                                 String date,
                                 String destination,
                                 Double maxPrice,
                                 String carrier,
                                 String timeRange,
                                 Boolean nonstop) {
        try {
            AgentService.SelectionCriteria c = new AgentService.SelectionCriteria();
            c.ordinal = ordinal;
            c.cheapest = cheapest;
            c.earliest = earliest;
            c.latest = latest;
            c.date = date;
            c.destination = destination;
            c.maxPrice = maxPrice;
            c.carrier = carrier;
            c.timeRange = timeRange;
            c.nonstop = nonstop;
            Map<String, Object> chosen = agentService.selectFromLast(memoryId, c);
            if (chosen == null) {
                return wrapError("NOT_FOUND", "No matching flight found in the last search (or no last search available).");
            }
            agentService.rememberChosen(memoryId, chosen);

            // Compute ordinal within filtered view
            java.util.List<java.util.Map<String, Object>> base = agentService.getLastSearch(memoryId);
            int ordinalComputed = 1;
            if (base != null && !base.isEmpty()) {
                java.util.List<java.util.Map<String, Object>> filtered = new java.util.ArrayList<>(base);
                if (date != null && !date.isBlank()) {
                    filtered.removeIf(f -> !String.valueOf(f.get("date")).startsWith(date));
                }
                if (destination != null && !destination.isBlank()) {
                    String dest = destination.trim();
                    filtered.removeIf(f -> !dest.equalsIgnoreCase(String.valueOf(f.get("destination"))));
                }
                if (maxPrice != null) {
                    double mp = maxPrice;
                    filtered.removeIf(f -> {
                        Object p = f.get("price");
                        double v = 0.0; try { v = ((Number) p).doubleValue(); } catch (Exception ignore) {}
                        return v > mp;
                    });
                }
                if (carrier != null && !carrier.isBlank()) {
                    String want = carrier.replace(" ", "").trim().toLowerCase(java.util.Locale.ROOT);
                    filtered.removeIf(f -> !String.valueOf(f.get("carrier")).replace(" ", "").trim().toLowerCase(java.util.Locale.ROOT).contains(want));
                }
                if (timeRange != null && !timeRange.isBlank()) {
                    String tr = timeRange.trim().toLowerCase(java.util.Locale.ROOT);
                    filtered.removeIf(f -> {
                        String dep = String.valueOf(f.get("departure"));
                        try {
                            java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(dep);
                            int h = odt.getHour();
                            return switch (tr) {
                                case "morning" -> (h < 5 || h >= 12);
                                case "evening" -> (h < 17 || h > 23);
                                default -> false;
                            };
                        } catch (Exception e) { return false; }
                    });
                }
                if (Boolean.TRUE.equals(nonstop)) {
                    filtered.removeIf(f -> {
                        Object s = f.get("stops");
                        if (s == null) return false;
                        int v = 0; try { v = Integer.parseInt(String.valueOf(s)); } catch (Exception ignore) {}
                        return v != 0;
                    });
                }
                int idx = -1;
                for (int i = 0; i < filtered.size(); i++) {
                    if (filtered.get(i) == chosen || filtered.get(i).equals(chosen)) { idx = i; break; }
                }
                if (idx >= 0) ordinalComputed = idx + 1;
            }
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("selected", chosen);
            payload.put("ordinal", ordinalComputed);
            payload.put("tripId", buildTripId(chosen));
            return wrapOk(payload);
        } catch (Exception e) {
            return wrapError("INTERNAL_ERROR", "Failed to select from last search: " + e.getMessage());
        }
    }
}
