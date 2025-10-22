package com.example.travel.assistant.service;

import com.example.travel.assistant.agent.TravelAssistantAgent;
import com.example.travel.assistant.tools.FlightSearchTool;
import com.example.travel.assistant.tools.BookingTools;
import com.example.travel.assistant.tools.InMemoryBookingTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);


    private final TravelAssistantAgent agent;
    private final AssistantService fallbackLlM;
    private final FlightSearchTool flightSearchTool;
    private final BookingTools bookingTools;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${assistant.server-nlu.enabled:${ASSISTANT_SERVER_NLU_ENABLED:false}}")
    private boolean serverNluEnabled;

    @Value("${assistant.agent.tools-enabled:${ASSISTANT_AGENT_TOOLS_ENABLED:true}}")
    private boolean agentToolsEnabled;

    // Session memory for last search results and last chosen flight (to support follow-ups like "first", "on Dec 12")
    private final ConcurrentHashMap<String, java.util.List<java.util.Map<String, Object>>> lastSearchBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.Map<String, Object>> lastChosenBySession = new ConcurrentHashMap<>();
    // Per-session active user id
    private final ConcurrentHashMap<String, String> userIdBySession = new ConcurrentHashMap<>();


    public static final class SelectionCriteria {
        public Integer ordinal; // 1-based
        public Boolean cheapest;
        public Boolean earliest;
        public Boolean latest;
        public String date; // YYYY-MM-DD
        public String destination; // IATA or city as stored in the list
    }



    public AgentService(@org.springframework.context.annotation.Lazy TravelAssistantAgent agent,
                     AssistantService fallbackLlM,
                     FlightSearchTool flightSearchTool,
                     BookingTools bookingTools) {
        this.agent = agent;
        this.fallbackLlM = fallbackLlM;
        this.flightSearchTool = flightSearchTool;
        this.bookingTools = bookingTools;
    }

    /**
     * Memory-aware ask: uses the provided memoryId to preserve conversation state.
     */
    public String ask(String memoryId, String prompt) {
        return ask(memoryId, prompt, null);
    }

    /**
     * Memory-aware ask with optional userId: persists user id per session and uses
     * built-in server intents (bookings, flight slot-filling) when tools are unavailable.
     */
    public String ask(String memoryId, String prompt, String userId) {
        // Remember/retain user id for this session if provided
        if (memoryId != null && userId != null && !userId.isBlank()) {
            userIdBySession.put(memoryId, userId.trim());
        }
        String currentUser = userIdBySession.get(memoryId);

        // Direct bookings intent path (works even if tools are unsupported in the model)
        if (isBookingsIntent(prompt)) {
            try {
                String raw = bookingTools.listBookings();
                java.util.List<java.util.Map<String, Object>> list;
                try {
                    list = mapper.readValue(raw, new TypeReference<java.util.List<java.util.Map<String, Object>>>(){});
                } catch (Exception parseEx) {
                    // booking-service may return non-JSON on error; surface it
                    return raw;
                }
                java.util.List<java.util.Map<String, Object>> filtered = list;
                if (currentUser != null && !currentUser.isBlank()) {
                    String uid = currentUser.trim();
                    filtered = new java.util.ArrayList<>();
                    for (var b : list) {
                        if (uid.equals(String.valueOf(b.get("userId")))) filtered.add(b);
                    }
                }
                if (filtered.isEmpty()) {
                    if (currentUser == null || currentUser.isBlank()) {
                        return "No bookings found. If you want me to filter by your account, tell me your user id (e.g., u-100).";
                    }
                    return "No bookings found for user " + currentUser + ".";
                }
                StringBuilder sb = new StringBuilder();
                if (currentUser != null && !currentUser.isBlank()) {
                    sb.append("Your bookings (user ").append(currentUser).append("):\n");
                } else {
                    sb.append("Bookings:\n");
                }
                int n = Math.min(filtered.size(), 5);
                for (int i = 0; i < n; i++) {
                    var b = filtered.get(i);
                    sb.append(" - ")
                      .append(String.valueOf(b.getOrDefault("id", "(id)")))
                      .append(": trip=").append(String.valueOf(b.getOrDefault("tripId", "?")))
                      .append(", price=").append(String.valueOf(b.getOrDefault("price", "?")))
                      .append("\n");
                }
                if (filtered.size() > n) sb.append("(+").append(filtered.size() - n).append(" more)\n");
                return sb.toString().trim();
            } catch (Exception e) {
                log.warn("[AgentService] bookings intent handling failed: {}", e.toString());
                // Fall through to agent/LLM if something went wrong
            }
        }

        // If agent tools are disabled via config, skip agent and use fallback LLM directly
        if (!agentToolsEnabled) {
            log.debug("[AgentService] Agent tools are disabled by config; using plain LLM.");
            return fallbackLlM.ask(prompt);
        }

        // Delegate to agent (LLM + tools)
        try {
            log.debug("[AgentService] Delegating to agent. memoryId={} prompt='{}'", memoryId, prompt);
            return agent.chat(memoryId, prompt);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            boolean toolUnsupported = containsIgnoreCase(msg, "tools are currently not supported")
                    || containsIgnoreCase(msg, "does not support tool")
                    || containsIgnoreCase(msg, "function calling is not supported")
                    || containsIgnoreCase(msg, "no such model")
                    || containsIgnoreCase(msg, "model not found");
            if (toolUnsupported) {
                log.warn("[AgentService] Tools unsupported for current model or model missing. Falling back to plain LLM. Error: {}", msg);
                return fallbackLlM.ask(prompt);
            }
            log.error("[AgentService] Agent failed without fallback condition. Re-throwing. Error: {}", msg, e);
            throw e;
        }
    }







    /**
     * Backward-compatible ask without memory id.
     */
    public String ask(String prompt) {
        return ask("default", prompt);
    }


    private boolean isBookingsIntent(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("my bookings") || t.contains("show my bookings") || t.contains("bookings")
                || t.matches(".*\\bbooking(s)?\\b.*");
    }

    private String[] extractRoute(String text) {
        if (text == null) return null;
        String t = text.trim();
        java.util.regex.Matcher mIata = java.util.regex.Pattern.compile("\\b([A-Z]{3})\\b\\s*(?:->|—|–|-| to |→|\u2192)?\\s*\\b([A-Z]{3})\\b").matcher(t);
        if (mIata.find()) {
            return new String[]{mIata.group(1), mIata.group(2)};
        }
        java.util.regex.Matcher mEn = java.util.regex.Pattern.compile("(?i)from\\s+([A-Za-z\\-\\s]+?)\\s+to\\s+([A-Za-z\\-\\s]+)").matcher(t);
        if (mEn.find()) {
            return new String[]{mEn.group(1).trim(), mEn.group(2).trim()};
        }
        return null;
    }

    private static boolean containsIgnoreCase(String src, String needle) {
        return src != null && src.toLowerCase().contains(needle.toLowerCase());
    }

    // ---- Session flight memory helpers ----
    public void rememberLastSearch(String memoryId, java.util.List<java.util.Map<String, Object>> flights) {
        if (memoryId == null || flights == null) return;
        lastSearchBySession.put(memoryId, flights);
    }
    public java.util.List<java.util.Map<String, Object>> getLastSearch(String memoryId) {
        return lastSearchBySession.get(memoryId);
    }
    public void rememberChosen(String memoryId, java.util.Map<String, Object> flight) {
        if (memoryId == null || flight == null) return;
        lastChosenBySession.put(memoryId, flight);
    }
    public java.util.Map<String, Object> getLastChosen(String memoryId) {
        return lastChosenBySession.get(memoryId);
    }

    public java.util.Map<String, Object> selectFromLast(String memoryId, SelectionCriteria c) {
        java.util.List<java.util.Map<String, Object>> list = lastSearchBySession.get(memoryId);
        if (list == null || list.isEmpty()) return null;
        java.util.List<java.util.Map<String, Object>> filtered = new java.util.ArrayList<>(list);
        if (c != null) {
            if (c.date != null && !c.date.isBlank()) {
                filtered.removeIf(f -> !String.valueOf(f.get("date")).startsWith(c.date));
            }
            if (c.destination != null && !c.destination.isBlank()) {
                String dest = c.destination.trim();
                filtered.removeIf(f -> {
                    String d = String.valueOf(f.get("destination"));
                    return !dest.equalsIgnoreCase(d);
                });
            }
        }
        if (filtered.isEmpty()) return null;
        if (c != null) {
            if (Boolean.TRUE.equals(c.cheapest)) {
                return filtered.stream().min(java.util.Comparator.comparingDouble(f -> ((Number) f.get("price")).doubleValue())).orElse(null);
            }
            if (Boolean.TRUE.equals(c.earliest)) {
                return filtered.stream().min(java.util.Comparator.comparing(f -> String.valueOf(f.get("departure")))).orElse(null);
            }
            if (Boolean.TRUE.equals(c.latest)) {
                return filtered.stream().max(java.util.Comparator.comparing(f -> String.valueOf(f.get("departure")))).orElse(null);
            }
            if (c.ordinal != null && c.ordinal >= 1 && c.ordinal <= filtered.size()) {
                return filtered.get(c.ordinal - 1);
            }
        }
        return filtered.get(0);
    }

}
