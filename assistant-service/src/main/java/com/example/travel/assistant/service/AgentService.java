package com.example.travel.assistant.service;

import com.example.travel.assistant.agent.TravelAssistantAgent;
import com.example.travel.assistant.tools.FlightSearchTool;
import com.example.travel.assistant.tools.BookingTools;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String OFFLINE_NOTICE = "I operate fully offline and cannot suggest external websites. Please provide origin (IATA), destination (IATA) and date (YYYY-MM-DD), and I'll use internal tools.";
    private static final Pattern ROUTE_DATE = Pattern.compile("from\\s+([A-Za-z]{3})\\s+to\\s+([A-Za-z]{3})\\s+on\\s+(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOK_INTENT = Pattern.compile("\\b(book|reserve|purchase|buy|забронируй|оформить|оформи|купить)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_ID = Pattern.compile("(?:user\\s*[:#]?\\s*([A-Za-z0-9_-]+))|(?:пользовател[ьяю]\\s*[:#]?\\s*([A-Za-z0-9_-]+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW_BOOKINGS = Pattern.compile("(?:\\b(show|list|display)\\s+(?:my\\s+)?bookings\\b)|(?:\\b(мои|покажи|список)\\s+бронирования?\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEW_BOOKING_QUERY = Pattern.compile("(where\\s+is\\s+my\\s+new\\s+booking)|(где\\s+мо[её]\\s+ново[её]\\s+бронировани[ея])", Pattern.CASE_INSENSITIVE);

    private final TravelAssistantAgent agent;
    private final AssistantService fallbackLlM;
    private final FlightSearchTool flightSearchTool;
    private final BookingTools bookingTools;
    private final ObjectMapper mapper = new ObjectMapper();

    // Per-session cache of last cheapest flight found (structured), keyed by memoryId
    private final ConcurrentHashMap<String, Map<String, Object>> lastCheapestBySession = new ConcurrentHashMap<>();

    // Per-session cache of last booking created (summary info)
    private final ConcurrentHashMap<String, Map<String, Object>> lastBookingBySession = new ConcurrentHashMap<>();

    // Per-session active user id (binds chat to a user code like u-100)
    private final ConcurrentHashMap<String, String> activeUserBySession = new ConcurrentHashMap<>();

    public AgentService(TravelAssistantAgent agent, AssistantService fallbackLlM, FlightSearchTool flightSearchTool, BookingTools bookingTools) {
        this.agent = agent;
        this.fallbackLlM = fallbackLlM;
        this.flightSearchTool = flightSearchTool;
        this.bookingTools = bookingTools;
    }

    /**
     * Memory-aware ask: uses the provided memoryId to preserve conversation state.
     */
    public String ask(String memoryId, String prompt) {
        // Fast-path A: if user clearly asked for a cheapest flight with all parameters, use internal dataset directly
        String direct = directFlightIfDetected(memoryId, prompt);
        if (direct != null) {
            return direct;
        }
        // Fast-path B: booking intent using last known flight
        String booked = directBookingIfDetected(memoryId, prompt);
        if (booked != null) {
            return booked;
        }
        // Fast-path C: list bookings for active user
        String listing = directShowBookingsIfDetected(memoryId, prompt);
        if (listing != null) {
            return listing;
        }
        String promptWithContext = prependUserContext(memoryId, prompt);
        try {
            log.debug("[AgentService] Using memoryId={} (activeUser={}) with prompt='{}'", memoryId, getActiveUser(memoryId), promptWithContext);
            String reply = agent.chat(memoryId, promptWithContext);
            return enforceOfflineAndToolUse(memoryId, reply, prompt);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            boolean toolUnsupported = containsIgnoreCase(msg, "tools are currently not supported")
                    || containsIgnoreCase(msg, "does not support tool")
                    || containsIgnoreCase(msg, "function calling is not supported")
                    || containsIgnoreCase(msg, "no such model")
                    || containsIgnoreCase(msg, "model not found");
            if (toolUnsupported) {
                log.warn("[AgentService] Tools unsupported for current model or model missing. Falling back to plain LLM. Error: {}", msg);
                return fallbackLlM.ask(promptWithContext);
            }
            log.error("[AgentService] Agent failed without fallback condition. Re-throwing. Error: {}", msg, e);
            throw e;
        }
    }

    private String enforceOfflineAndToolUse(String memoryId, String reply, String userPrompt) {
        if (reply == null) return OFFLINE_NOTICE;
        String lc = reply.toLowerCase(Locale.ROOT);
        boolean suggestsWeb = lc.contains("google flights") || lc.contains("skyscanner") || lc.contains("kayak") || lc.contains("momondo")
                || lc.contains("website") || lc.contains("search engine") || lc.contains("browse the internet");
        if (!suggestsWeb) return reply;

        // Try to extract route + date from user's prompt and use internal dataset directly
        try {
            Matcher m = ROUTE_DATE.matcher(userPrompt != null ? userPrompt : "");
            if (m.find()) {
                String origin = m.group(1).toUpperCase(Locale.ROOT);
                String dest = m.group(2).toUpperCase(Locale.ROOT);
                String date = m.group(3);
                String json = flightSearchTool.cheapestFlight(origin, dest, date);
                Map<String, Object> obj = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                if (obj.containsKey("carrier")) {
                    // Cache structured result for follow-up booking
                    lastCheapestBySession.put(memoryId != null ? memoryId : "default", obj);

                    String carrier = String.valueOf(obj.get("carrier"));
                    String flightNumber = String.valueOf(obj.get("flightNumber"));
                    String departure = String.valueOf(obj.get("departure"));
                    String arrival = String.valueOf(obj.get("arrival"));
                    Object price = obj.get("price");
                    Object currency = obj.getOrDefault("currency", "USD");
                    return "Cheapest flight on " + date + " " + origin + "→" + dest + ": " + carrier + " " + flightNumber + ", depart " + departure + ", arrive " + arrival + ", price " + price + " " + currency + ".";
                } else if (obj.containsKey("message")) {
                    return "Offline result: " + String.valueOf(obj.get("message"));
                }
            }
        } catch (Exception ex) {
            log.warn("[AgentService] Offline enforcement failed to parse/compute tool result: {}", ex.toString());
        }
        return OFFLINE_NOTICE;
    }

    private String directFlightIfDetected(String memoryId, String userPrompt) {
        try {
            if (userPrompt == null || userPrompt.isBlank()) return null;
            Matcher m = ROUTE_DATE.matcher(userPrompt);
            if (!m.find()) return null;
            String origin = m.group(1).toUpperCase(Locale.ROOT);
            String dest = m.group(2).toUpperCase(Locale.ROOT);
            String date = m.group(3);
            String json = flightSearchTool.cheapestFlight(origin, dest, date);
            Map<String, Object> obj = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (obj.containsKey("carrier")) {
                // Cache for booking follow-up
                lastCheapestBySession.put(memoryId != null ? memoryId : "default", obj);

                String carrier = String.valueOf(obj.get("carrier"));
                String flightNumber = String.valueOf(obj.get("flightNumber"));
                String departure = String.valueOf(obj.get("departure"));
                String arrival = String.valueOf(obj.get("arrival"));
                Object price = obj.get("price");
                Object currency = obj.getOrDefault("currency", "USD");
                return "Cheapest flight on " + date + " " + origin + "→" + dest + ": " + carrier + " " + flightNumber + ", depart " + departure + ", arrive " + arrival + ", price " + price + " " + currency + ".";
            } else if (obj.containsKey("message")) {
                return "Offline result: " + String.valueOf(obj.get("message"));
            }
        } catch (Exception ex) {
            log.warn("[AgentService] directFlightIfDetected failed: {}", ex.toString());
        }
        return null;
    }

    private String directBookingIfDetected(String memoryId, String userPrompt) {
        try {
            if (userPrompt == null || userPrompt.isBlank()) return null;
            if (!BOOK_INTENT.matcher(userPrompt).find()) return null;

            String key = memoryId != null ? memoryId : "default";
            Map<String, Object> flight = lastCheapestBySession.get(key);
            if (flight == null || !flight.containsKey("flightNumber")) {
                return "I can book the cheapest flight, but I need you to search a flight first (origin, destination, date) or specify the flight details.";
            }

            // Parse userId from prompt
            Matcher mu = USER_ID.matcher(userPrompt);
            String userId = null;
            if (mu.find()) {
                userId = mu.group(1) != null ? mu.group(1) : mu.group(2);
            }
            if (userId == null || userId.isBlank()) {
                String active = getActiveUser(memoryId);
                if (active != null && !active.isBlank()) {
                    userId = active;
                } else {
                    return "To proceed with booking, please provide userId (e.g., u-100).";
                }
            }

            String origin = String.valueOf(flight.getOrDefault("origin", ""));
            String dest = String.valueOf(flight.getOrDefault("destination", ""));
            String date = String.valueOf(flight.getOrDefault("date", ""));
            String flightNumber = String.valueOf(flight.getOrDefault("flightNumber", ""));
            double price = 0.0;
            try { price = ((Number) flight.get("price")).doubleValue(); } catch (Exception ignore) {}

            String tripId = String.format("%s-%s-%s-%s", origin, dest, date, flightNumber);
            String resp = bookingTools.createBooking(userId, tripId, price);

            // Cache last booking summary for this session
            try {
                Map<String, Object> summary = new java.util.HashMap<>();
                summary.put("userId", userId);
                summary.put("tripId", tripId);
                summary.put("price", price);
                if (resp != null && resp.trim().startsWith("{")) {
                    Map<String, Object> r = mapper.readValue(resp, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    Object bid = r.get("bookingId");
                    if (bid != null) summary.put("bookingId", String.valueOf(bid));
                }
                lastBookingBySession.put(key, summary);
            } catch (Exception ignore) { }

            // Build confirmation message
            String confirmation;
            if (resp != null && resp.contains("bookingId")) {
                confirmation = "Booked successfully for user " + userId + ". Trip: " + tripId + ". Response: " + resp;
            } else {
                confirmation = "Booking response: " + (resp == null ? "(no response)" : resp);
            }
            return confirmation;
        } catch (Exception ex) {
            log.warn("[AgentService] directBookingIfDetected failed: {}", ex.toString());
            return null;
        }
    }

    private String directShowBookingsIfDetected(String memoryId, String userPrompt) {
        try {
            if (userPrompt == null || userPrompt.isBlank()) return null;
            boolean wantsList = SHOW_BOOKINGS.matcher(userPrompt).find() || NEW_BOOKING_QUERY.matcher(userPrompt).find();
            if (!wantsList) return null;

            // Resolve userId from prompt or active session
            Matcher mu = USER_ID.matcher(userPrompt);
            String userId = null;
            if (mu.find()) {
                userId = mu.group(1) != null ? mu.group(1) : mu.group(2);
            }
            if (userId == null || userId.isBlank()) {
                String active = getActiveUser(memoryId);
                if (active != null && !active.isBlank()) {
                    userId = active;
                } else {
                    return "Please provide userId (e.g., u-100) to show bookings.";
                }
            }

            String json = bookingTools.listBookings();
            if (json == null || json.isBlank()) return "No bookings found (empty response).";

            java.util.List<java.util.Map<String, Object>> all = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            if (all == null) all = java.util.List.of();
            final String uid = userId;
            java.util.List<java.util.Map<String, Object>> mine = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> b : all) {
                if (b == null) continue;
                Object u = b.get("userId");
                if (u != null && uid.equalsIgnoreCase(String.valueOf(u))) {
                    mine.add(b);
                }
            }
            // Sort by createdAt desc if present
            mine.sort((a, b) -> String.valueOf(b.getOrDefault("createdAt", "")).compareTo(String.valueOf(a.getOrDefault("createdAt", ""))));

            if (mine.isEmpty()) {
                // If user explicitly asked about the new booking and we cached one, surface it
                String key = memoryId != null ? memoryId : "default";
                java.util.Map<String, Object> last = lastBookingBySession.get(key);
                if (last != null && uid.equalsIgnoreCase(String.valueOf(last.getOrDefault("userId", "")))) {
                    String tid = String.valueOf(last.getOrDefault("tripId", "(unknown)"));
                    String bid = String.valueOf(last.getOrDefault("bookingId", "(pending id)"));
                    Object price = last.get("price");
                    return "Latest booking (cached): trip=" + tid + ", bookingId=" + bid + ", price=" + price + ".";
                }
                return "No bookings found for user " + uid + ".";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Bookings for user ").append(uid).append(" (" ).append(mine.size()).append("):\n");
            int max = Math.min(10, mine.size());
            for (int i = 0; i < max; i++) {
                var b = mine.get(i);
                String id = String.valueOf(b.getOrDefault("id", ""));
                String trip = String.valueOf(b.getOrDefault("tripId", ""));
                Object price = b.get("price");
                String created = String.valueOf(b.getOrDefault("createdAt", ""));
                sb.append(i + 1).append(") ")
                  .append(trip)
                  .append(" — price ").append(price)
                  .append(created.isBlank() ? "" : (" — created " + created))
                  .append(id.isBlank() ? "" : (" — id " + id))
                  .append("\n");
            }
            if (mine.size() > max) {
                sb.append("… and ").append(mine.size() - max).append(" more");
            }
            return sb.toString().trim();
        } catch (Exception ex) {
            log.warn("[AgentService] directShowBookingsIfDetected failed: {}", ex.toString());
            return null;
        }
    }

    /**
     * Bind an active user id to a session (memoryId), e.g. "u-100".
     */
    public void setActiveUser(String sessionId, String userId) {
        if (userId != null && !userId.isBlank()) {
            String key = sessionId != null ? sessionId : "default";
            activeUserBySession.put(key, userId);
        }
    }

    private String getActiveUser(String sessionId) {
        return activeUserBySession.get(sessionId != null ? sessionId : "default");
    }

    private String prependUserContext(String memoryId, String prompt) {
        String uid = getActiveUser(memoryId);
        if (uid == null || uid.isBlank()) return prompt;
        String base = prompt == null ? "" : prompt;
        return "Active userId: " + uid + "\n" + base;
    }

    /**
     * Backward-compatible ask without memory id.
     */
    public String ask(String prompt) {
        return ask("default", prompt);
    }

    private static boolean containsIgnoreCase(String src, String needle) {
        return src != null && src.toLowerCase().contains(needle.toLowerCase());
    }
}
