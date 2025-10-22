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
    // Per-session last created/used booking id
    private final ConcurrentHashMap<String, String> lastBookingIdBySession = new ConcurrentHashMap<>();
    // Reschedule workflow memory per session: target booking and target date
    private final ConcurrentHashMap<String, String> rescheduleTargetBookingIdBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> rescheduleNewDateBySession = new ConcurrentHashMap<>();
    // Pending cancellation candidate list (so user can answer with just "first/2nd/last")
    private final ConcurrentHashMap<String, java.util.List<java.util.Map<String, Object>>> cancelCandidatesBySession = new ConcurrentHashMap<>();


    public static final class SelectionCriteria {
        public Integer ordinal; // 1-based
        public Boolean cheapest;
        public Boolean earliest;
        public Boolean latest;
        public String date; // YYYY-MM-DD
        public String destination; // IATA or city as stored in the list
        public Double maxPrice; // filter: price <= maxPrice
        public String carrier; // filter by carrier name (case-insensitive, ignore spaces)
        public String timeRange; // optional: "morning" (05:00-11:59) | "evening" (17:00-23:59)
        public Boolean nonstop; // if data has stops==0
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
        // Basic guard against empty input to avoid unexpected NPEs in downstream paths
        if (prompt == null || prompt.isBlank()) {
            return "Please provide a question or instruction (prompt cannot be empty).";
        }

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
                    String id = String.valueOf(b.getOrDefault("id", "(id)"));
                    String tripId = String.valueOf(b.getOrDefault("tripId", "?"));
                    String priceStr = String.valueOf(b.getOrDefault("price", "?"));
                    // Enrich with flight details from dataset
                    java.util.Map<String, Object> f = flightSearchTool.lookupFlightByTripId(tripId);
                    if (f != null && !f.isEmpty()) {
                        String carrier = String.valueOf(f.getOrDefault("carrier", "?"));
                        String flightNum = String.valueOf(f.getOrDefault("flightNumber", "?"));
                        String dateVal = String.valueOf(f.getOrDefault("date", "?"));
                        String origin = String.valueOf(f.getOrDefault("origin", "?"));
                        String destination = String.valueOf(f.getOrDefault("destination", "?"));
                        String depIso = String.valueOf(f.getOrDefault("departure", ""));
                        String arrIso = String.valueOf(f.getOrDefault("arrival", ""));
                        String currency = String.valueOf(f.getOrDefault("currency", "")).trim();
                        String depT = depIso;
                        String arrT = arrIso;
                        try {
                            java.time.OffsetDateTime od = java.time.OffsetDateTime.parse(depIso);
                            depT = String.format(java.util.Locale.ROOT, "%02d:%02d", od.getHour(), od.getMinute());
                        } catch (Exception ignore) {}
                        try {
                            java.time.OffsetDateTime oa = java.time.OffsetDateTime.parse(arrIso);
                            arrT = String.format(java.util.Locale.ROOT, "%02d:%02d", oa.getHour(), oa.getMinute());
                        } catch (Exception ignore) {}
                        // Prefer booking price if present; otherwise fallback to flight price
                        if (priceStr == null || priceStr.isBlank() || "?".equals(priceStr)) {
                            Object p = f.get("price");
                            if (p != null) priceStr = String.valueOf(p);
                        }
                        sb.append(i + 1).append(") ")
                          .append(carrier).append(' ').append(flightNum)
                          .append(" — ").append(origin).append(" -> ").append(destination)
                          .append(" on ").append(dateVal)
                          .append(" (dep ").append(depT).append(", arr ").append(arrT).append(")")
                          .append(", price=").append(priceStr);
                        if (!currency.isBlank()) sb.append(' ').append(currency);
                        sb.append("\n   id: ").append(id).append("\n");
                    } else {
                        // Fallback when we cannot resolve the tripId to a dataset flight
                        sb.append(i + 1).append(") ")
                          .append("trip=").append(tripId)
                          .append(", price=").append(priceStr)
                          .append("\n   id: ").append(id).append("\n");
                    }
                }
                if (filtered.size() > n) sb.append("(+").append(filtered.size() - n).append(" more)\n");
                return sb.toString().trim();
            } catch (Exception e) {
                log.warn("[AgentService] bookings intent handling failed: {}", e.toString());
                // Fall through to agent/LLM if something went wrong
            }
        }
        
        // Booking create/cancel/reschedule intents (server-side safety net)
        if (prompt != null && !prompt.isBlank()) {
            String lower = prompt.toLowerCase(Locale.ROOT);

            // Reschedule flow: start or continue
            if (isRescheduleIntent(lower, memoryId)) {
                // Determine target booking id
                String targetId = extractUuid(prompt);
                if (targetId == null) {
                    // use explicit 'last' if present
                    boolean wantLast = lower.contains("last") || lower.contains("послед");
                    if (wantLast) targetId = getLastBookingId(memoryId);
                }
                if (targetId == null || targetId.isBlank()) {
                    // fallback to remembered target in session
                    targetId = rescheduleTargetBookingIdBySession.get(memoryId);
                }

                String newDate = parseDate(prompt);
                if (newDate == null) {
                    // if we don't know the target yet either, try to default to last booking id
                    if (targetId == null || targetId.isBlank()) {
                        String lastB = getLastBookingId(memoryId);
                        if (lastB != null && !lastB.isBlank()) {
                            targetId = lastB;
                        }
                    }
                    if (targetId == null || targetId.isBlank()) {
                        return "Which booking should I reschedule? Provide the booking id (UUID) or say 'reschedule last'.";
                    }
                    // remember target and ask for date
                    rescheduleTargetBookingIdBySession.put(memoryId, targetId);
                    return "What date should I move it to? Please provide YYYY-MM-DD.";
                }

                // We have new date; ensure target id known
                if (targetId == null || targetId.isBlank()) {
                    return "Which booking should I reschedule? Provide the booking id (UUID) or say 'reschedule last'.";
                }

                // Fetch booking details to get tripId and infer route
                String bookingJson = bookingTools.getBooking(targetId);
                try {
                    java.util.Map<String, Object> booking = mapper.readValue(bookingJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){ });
                    // Ownership check if userId is known
                    if (currentUser != null && !currentUser.isBlank()) {
                        Object owner = booking.get("userId");
                        if (owner != null && !currentUser.equals(String.valueOf(owner))) {
                            return "You can only reschedule your own bookings. This booking belongs to user " + owner + ".";
                        }
                    }
                    Object tripIdObj = booking.get("tripId");
                    if (tripIdObj == null) {
                        // If response is not JSON booking, just surface it
                        if (bookingJson != null && !bookingJson.startsWith("{")) return bookingJson;
                        return "I could not find that booking. Please check the id.";
                    }
                    String tripId = String.valueOf(tripIdObj);
                    // Try to infer route from dataset by tripId
                    java.util.Map<String, Object> original = flightSearchTool.lookupFlightByTripId(tripId);
                    String origin = (original != null && original.get("origin") != null) ? String.valueOf(original.get("origin")) : null;
                    String destination = (original != null && original.get("destination") != null) ? String.valueOf(original.get("destination")) : null;
                    if (origin == null || destination == null || origin.isBlank() || destination.isBlank()) {
                        // try from last chosen if it matches the same tripId
                        java.util.Map<String, Object> lastChosen = getLastChosen(memoryId);
                        if (lastChosen != null) {
                            String lcTrip = buildTripId(lastChosen);
                            if (lcTrip != null && lcTrip.equalsIgnoreCase(tripId)) {
                                origin = String.valueOf(lastChosen.get("origin"));
                                destination = String.valueOf(lastChosen.get("destination"));
                            }
                        }
                    }
                    if (origin == null || destination == null || origin.isBlank() || destination.isBlank()) {
                        rescheduleTargetBookingIdBySession.put(memoryId, targetId);
                        rescheduleNewDateBySession.put(memoryId, newDate);
                        return "I couldn't infer the route from this booking. Tell me the origin and destination, e.g., 'from SFO to JFK'.";
                    }

                    String json = flightSearchTool.searchFlights(origin, destination, newDate);
                    java.util.List<java.util.Map<String, Object>> list = unwrapDataList(json);
                    if (list == null || list.isEmpty()) {
                        return "No flights found for " + origin + " -> " + destination + " on " + newDate + ". Try another date.";
                    }
                    rememberLastSearch(memoryId, list);
                    rescheduleTargetBookingIdBySession.put(memoryId, targetId);
                    rescheduleNewDateBySession.put(memoryId, newDate);
                    return formatList(list, "reschedule");
                } catch (Exception ex) {
                    return bookingJson; // surface error
                }
            }

            // Reschedule confirmation (after user selected an option)
            if (isRescheduleConfirmIntent(lower, memoryId)) {
                String targetId = rescheduleTargetBookingIdBySession.get(memoryId);
                if (targetId == null || targetId.isBlank()) {
                    return "There is no pending reschedule. Say 'reschedule booking <id> to YYYY-MM-DD' to start.";
                }
                // Determine chosen flight
                java.util.Map<String, Object> chosen = getLastChosen(memoryId);
                if (chosen == null) {
                    Integer ord = parseOrdinal(lower);
                    var lastList = getLastSearch(memoryId);
                    if (ord != null && lastList != null && ord >= 1 && ord <= lastList.size()) {
                        chosen = lastList.get(ord - 1);
                        rememberChosen(memoryId, chosen);
                    }
                }
                if (chosen == null) {
                    return "Please select a flight from the list first (e.g., 'first' or '2nd').";
                }
                String currentUser2 = currentUser;
                if (currentUser2 == null || currentUser2.isBlank()) {
                    return "To reschedule, tell me your user id first (e.g., u-100).";
                }
                // Cancel old then create new
                // Transactional-ish reschedule: create new booking first, then cancel old; rollback on cancel failure
                String newTripId = buildTripId(chosen);
                double price = 0.0;
                try { price = ((Number) chosen.getOrDefault("price", 0.0)).doubleValue(); } catch (Exception ignore) {}
                String createResp = bookingTools.createBooking(currentUser2, newTripId, price);
                // Extract new booking id from structured response
                String newBookingId = extractBookingIdFromResponse(createResp);
                // If creation failed, surface response and do not cancel old
                if (newBookingId == null || newBookingId.isBlank()) {
                    return createResp != null ? createResp : ("Failed to create a new booking for trip=" + newTripId);
                }
                // Validate ownership of the old booking before canceling when userId is known
                if (currentUser2 != null && !currentUser2.isBlank()) {
                    String bJson = bookingTools.getBooking(targetId);
                    try {
                        java.util.Map<String, Object> b = mapper.readValue(bJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){ });
                        Object bUser = b.get("userId");
                        if (bUser != null && !currentUser2.equals(String.valueOf(bUser))) {
                            // Rollback newly created booking since ownership mismatch
                            bookingTools.deleteBooking(newBookingId);
                            return "You can only change your own bookings. The target booking belongs to user " + bUser + ".";
                        }
                    } catch (Exception ignore) {
                        // If cannot parse, proceed with caution
                    }
                }
                String delResp = bookingTools.deleteBooking(targetId);
                // Determine if delete succeeded by checking status field if present
                boolean deleteOk = isToolOk(delResp);
                if (!deleteOk) {
                    // Rollback: delete the new booking we just created
                    bookingTools.deleteBooking(newBookingId);
                    return (delResp != null && !delResp.isBlank())
                            ? ("Reschedule failed to cancel the old booking; rolled back the new one. Details: " + delResp)
                            : "Reschedule failed to cancel the old booking; rolled back the new one.";
                }
                // Success: remember new booking id and clear state
                rememberLastBooking(memoryId, newBookingId);
                rescheduleTargetBookingIdBySession.remove(memoryId);
                rescheduleNewDateBySession.remove(memoryId);
                return "Rescheduled. Old booking cancelled and new booking created: " + newBookingId + " (trip=" + newTripId + ")";
            }

            // Create booking like: "book it", "book the first", "забронируй"
            if (isBookingCreateIntent(lower)) {
                // Prefer the last explicitly chosen flight
                Map<String, Object> chosen = getLastChosen(memoryId);
                // If user referenced an ordinal and we have a last search list, use it
                if (chosen == null) {
                    Integer ord = parseOrdinal(lower);
                    if (ord != null) {
                        var last = getLastSearch(memoryId);
                        if (last != null && ord >= 1 && ord <= last.size()) {
                            chosen = last.get(ord - 1);
                            rememberChosen(memoryId, chosen);
                        }
                    }
                }
                if (chosen == null) {
                    return "To book, please select a flight from the last results (e.g., 'first' or '2nd') or tell me the booking id/flight details.";
                }
                if (currentUser == null || currentUser.isBlank()) {
                    return "I can book it. Tell me your user id first (e.g., u-100).";
                }
                String tripId = buildTripId(chosen);
                double price = 0.0;
                try { price = ((Number) chosen.getOrDefault("price", 0.0)).doubleValue(); } catch (Exception ignore) {}
                String resp = bookingTools.createBooking(currentUser, tripId, price);
                // Remember last booking id for quick cancellation (supports wrapped tool response)
                try {
                    String bid = extractBookingIdFromResponse(resp);
                    if (bid != null && !bid.isBlank()) {
                        rememberLastBooking(memoryId, bid);
                    }
                } catch (Exception ignore) {}
                return resp != null ? resp : ("Booking requested for user " + currentUser + ", trip=" + tripId + ".");
            }
            // Cancel booking like: "cancel booking <id>", "delete booking", "отмени бронь"
            if (isCancelBookingIntent(lower)) {
                String id = extractUuid(prompt);
                if (id != null) {
                    // Ownership validation when userId is provided
                    if (currentUser != null && !currentUser.isBlank()) {
                        String bJson = bookingTools.getBooking(id);
                        try {
                            java.util.Map<String, Object> b = mapper.readValue(bJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){ });
                            Object bUser = b.get("userId");
                            if (bUser != null && !currentUser.equals(String.valueOf(bUser))) {
                                return "You can only cancel your own bookings. This booking belongs to user " + bUser + ".";
                            }
                        } catch (Exception ignore) {}
                    }
                    return bookingTools.deleteBooking(id);
                }
                // Try last created/used booking id first
                String lastId = getLastBookingId(memoryId);
                if (lastId != null && !lastId.isBlank()) {
                    return bookingTools.deleteBooking(lastId);
                }
                // If no id provided, try to help user pick one
                try {
                    String raw = bookingTools.listBookings();
                    java.util.List<java.util.Map<String, Object>> list = mapper.readValue(raw, new TypeReference<java.util.List<java.util.Map<String, Object>>>(){});
                    java.util.List<java.util.Map<String, Object>> filtered = list;
                    if (currentUser != null && !currentUser.isBlank()) {
                        String uid = currentUser.trim();
                        filtered = new java.util.ArrayList<>();
                        for (var b : list) {
                            if (uid.equals(String.valueOf(b.get("userId")))) filtered.add(b);
                        }
                    }
                    if (filtered.isEmpty()) return "You have no bookings to cancel.";

                    // Ordinal selection support (e.g., "cancel first", "cancel 2nd")
                    Integer ord = parseOrdinal(lower);
                    boolean lastWord = lower.contains("last") || lower.contains("послед");
                    if (ord != null && ord >= 1 && ord <= filtered.size()) {
                        String bid = String.valueOf(filtered.get(ord - 1).get("id"));
                        return bookingTools.deleteBooking(bid);
                    } else if (lastWord) {
                        String bid = String.valueOf(filtered.get(filtered.size() - 1).get("id"));
                        return bookingTools.deleteBooking(bid);
                    }

                    if (filtered.size() == 1) {
                        String bid = String.valueOf(filtered.get(0).get("id"));
                        String delResp = bookingTools.deleteBooking(bid);
                        return delResp != null ? delResp : ("Booking deleted: " + bid);
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("Which booking id should I cancel? Here are recent ids:\n");
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
                    sb.append("Reply with: cancel booking <id> or e.g. 'cancel first'/'cancel last'.");
                    // Remember the displayed subset so follow-up like 'first one' works
                    java.util.List<java.util.Map<String, Object>> subset = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) subset.add(filtered.get(i));
                    cancelCandidatesBySession.put(memoryId, subset);
                    return sb.toString().trim();
                } catch (Exception e) {
                    return "Please provide the booking id to cancel (UUID).";
                }
            }
        }
        
        // Quick ordinal-only selection that works even when server NLU is disabled
        if (prompt != null && !prompt.isBlank()) {
            String lowerSel = prompt.toLowerCase(Locale.ROOT);
            Integer ordSel = parseOrdinal(lowerSel);
            boolean isLastWord = lowerSel.contains("last") || lowerSel.contains("послед");

            // 1) If there is a pending cancel choice, apply ordinal/last to that list
            java.util.List<java.util.Map<String, Object>> cancelList = cancelCandidatesBySession.get(memoryId);
            if (cancelList != null && !cancelList.isEmpty() && (ordSel != null || isLastWord)) {
                int idx;
                if (isLastWord) {
                    idx = cancelList.size() - 1;
                } else {
                    idx = Math.min(Math.max(ordSel - 1, 0), cancelList.size() - 1);
                }
                String bid = String.valueOf(cancelList.get(idx).get("id"));
                cancelCandidatesBySession.remove(memoryId);
                return bookingTools.deleteBooking(bid);
            }

            // 2) Otherwise, treat ordinal as flight selection from last search
            java.util.List<java.util.Map<String, Object>> lastList = getLastSearch(memoryId);
            if (ordSel != null && lastList != null && !lastList.isEmpty() && ordSel >= 1 && ordSel <= lastList.size()) {
                java.util.Map<String, Object> chosenSel = lastList.get(ordSel - 1);
                rememberChosen(memoryId, chosenSel);
                String action = rescheduleTargetBookingIdBySession.containsKey(memoryId) ? "reschedule" : "book";
                return formatSelection(chosenSel, action);
            }
        }

        // Server-side NLU for reliable flight search and selection (pre-agent)
        if (serverNluEnabled && prompt != null && !prompt.isBlank()) {
            String lower = prompt.toLowerCase(Locale.ROOT);

            // Ordinal selection like "first", "2nd", etc., based on last search
            java.util.Map<String, Object> selected = null;
            java.util.List<java.util.Map<String, Object>> last = getLastSearch(memoryId);
            if (last != null && !last.isEmpty()) {
                Integer ord = parseOrdinal(lower);
                if (ord != null && ord >= 1 && ord <= last.size()) {
                    selected = last.get(ord - 1);
                }
            }
            if (selected != null) {
                rememberChosen(memoryId, selected);
                return formatSelection(selected);
            }

            // Advice/Recommendation intent (e.g., "advise me where to fly from New York", "recommend a flight", RU: "посоветуй", "куда лететь")
            boolean adviseIntent = lower.contains("advise") || lower.contains("recommend") || lower.contains("suggest");
            if (adviseIntent) {
                String originOnly = extractOriginOnly(prompt);
                String dateOpt = parseDate(prompt);
                if (originOnly == null) {
                    return "I can suggest destinations. Tell me your origin city or IATA code" + (dateOpt == null ? " and date (YYYY-MM-DD)." : ".");
                }
                boolean askBest = lower.contains("best") || lower.contains("cheapest") || lower.contains("lowest");
                String json = askBest
                        ? flightSearchTool.recommendFromOrigin(originOnly, dateOpt)
                        : flightSearchTool.suggestDestinations(originOnly, dateOpt, 5);
                try {
                    if (askBest) {
                        java.util.Map<String, Object> obj = mapper.readValue(json, new TypeReference<java.util.Map<String, Object>>(){});
                        if (obj == null || obj.isEmpty() || obj.get("price") == null) {
                            return "No recommendations found from " + originOnly + (dateOpt != null ? (" on " + dateOpt) : "") + ".";
                        }
                        rememberLastSearch(memoryId, java.util.List.of(obj));
                        rememberChosen(memoryId, obj);
                        return "Recommended: " + formatOne(obj);
                    } else {
                        java.util.List<java.util.Map<String, Object>> list = mapper.readValue(json, new TypeReference<java.util.List<java.util.Map<String, Object>>>(){});
                        if (list == null || list.isEmpty()) {
                            return "No recommendations found from " + originOnly + (dateOpt != null ? (" on " + dateOpt) : "") + ".";
                        }
                        rememberLastSearch(memoryId, list);
                        return formatList(list);
                    }
                } catch (Exception e) {
                    log.warn("[AgentService] Failed to parse advice response: {}", e.toString());
                    return json;
                }
            }

            boolean cheapestIntent = lower.contains("cheapest") || lower.contains("lowest");
            boolean flightIntent = cheapestIntent
                    || lower.contains("flight")
                    || extractRoute(prompt) != null;

            if (flightIntent) {
                String[] route = extractRoute(prompt);
                String origin = route != null ? route[0] : null;
                String destination = route != null ? route[1] : null;
                String date = parseDate(prompt);

                // Ask for missing slots first
                if (origin == null || destination == null || date == null) {
                    return followupForMissing(origin, destination, date);
                }

                String json = cheapestIntent
                        ? flightSearchTool.cheapestFlight(origin, destination, date)
                        : flightSearchTool.searchFlights(origin, destination, date);

                try {
                    if (cheapestIntent) {
                        java.util.Map<String, Object> obj = unwrapDataObject(json);
                        if (obj == null || obj.isEmpty() || obj.get("price") == null) {
                            return "No flights found for " + origin + " -> " + destination + " on " + date + ". Try another date or nearby airport.";
                        }
                        rememberLastSearch(memoryId, java.util.List.of(obj));
                        rememberChosen(memoryId, obj);
                        return formatOne(obj);
                    } else {
                        java.util.List<java.util.Map<String, Object>> list = unwrapDataList(json);
                        if (list == null || list.isEmpty()) {
                            return "No flights found for " + origin + " -> " + destination + " on " + date + ". Try another date or nearby airport.";
                        }
                        rememberLastSearch(memoryId, list);
                        return formatList(list);
                    }
                } catch (Exception parse) {
                    log.warn("[AgentService] Failed to parse flight tool response: {}", parse.toString());
                    return json;
                }
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
            com.example.travel.assistant.memory.ConversationContext.setMemoryId(memoryId);
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
        } finally {
            com.example.travel.assistant.memory.ConversationContext.clear();
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
        String t = text.toLowerCase(Locale.ROOT).trim();
        // Exclude cancellation/deletion intents explicitly
        if (t.contains("cancel") || t.contains("delete") || t.contains("remove")
                || t.contains("отмен") || t.contains("удал")) {
            return false;
        }
        // Positive signals for listing bookings (EN/RU), avoid generic single word 'booking'
        if (t.contains("show my bookings") || t.contains("show bookings") || t.contains("list bookings")
                || t.contains("my bookings") || t.contains("мои брони") || t.contains("мои бронирования")
                || t.contains("покажи брони") || t.contains("список брони") || t.contains("список бронирований")) {
            return true;
        }
        // Exact or near-exact short queries
        if (t.equals("bookings") || t.equals("my bookings") || t.equals("список броней") || t.equals("брони") || t.equals("мои брони")) {
            return true;
        }
        return false;
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

    public void rememberLastBooking(String memoryId, String bookingId) {
        if (memoryId == null || bookingId == null || bookingId.isBlank()) return;
        lastBookingIdBySession.put(memoryId, bookingId);
    }
    public String getLastBookingId(String memoryId) {
        return lastBookingIdBySession.get(memoryId);
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
            if (c.maxPrice != null) {
                double maxP = c.maxPrice;
                filtered.removeIf(f -> {
                    Object p = f.get("price");
                    double v = 0.0;
                    try { v = ((Number) p).doubleValue(); } catch (Exception ignore) {}
                    return v > maxP;
                });
            }
            if (c.carrier != null && !c.carrier.isBlank()) {
                String want = c.carrier.replace(" ", "").trim().toLowerCase(java.util.Locale.ROOT);
                filtered.removeIf(f -> {
                    String cur = String.valueOf(f.get("carrier")).replace(" ", "").trim().toLowerCase(java.util.Locale.ROOT);
                    return !cur.contains(want);
                });
            }
            if (c.timeRange != null && !c.timeRange.isBlank()) {
                String tr = c.timeRange.trim().toLowerCase(java.util.Locale.ROOT);
                filtered.removeIf(f -> {
                    String dep = String.valueOf(f.get("departure"));
                    try {
                        java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(dep);
                        int h = odt.getHour();
                        return switch (tr) {
                            case "morning" -> (h < 5 || h >= 12);
                            case "evening" -> (h < 17 || h > 23);
                            default -> false; // unsupported ranges are ignored
                        };
                    } catch (Exception e) {
                        return false;
                    }
                });
            }
            if (Boolean.TRUE.equals(c.nonstop)) {
                filtered.removeIf(f -> {
                    Object stops = f.get("stops");
                    if (stops == null) return false; // default to allow when unknown
                    int s = 0; try { s = Integer.parseInt(String.valueOf(stops)); } catch (Exception ignore) {}
                    return s != 0;
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

    // ---- Server-NLU helpers ----
    private Integer parseOrdinal(String textLower) {
        if (textLower == null) return null;
        // English words
        if (textLower.contains("first")) return 1;
        if (textLower.contains("second")) return 2;
        if (textLower.contains("third")) return 3;
        if (textLower.contains("fourth")) return 4;
        if (textLower.contains("fifth")) return 5;
        // Numeric forms like 1st/2nd/3rd/4th or plain numbers possibly preceded by 'option/number/#'
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:^|\\s)(?:option|number|#)?\\s*(\\d+)(?:st|nd|rd|th)?\\b")
                .matcher(textLower);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        }
        return null;
    }

    private boolean isRescheduleIntent(String lower, String memoryId) {
        if (lower == null) return false;
        boolean keyword = lower.contains("reschedule") || lower.contains("rebook") || lower.contains("move flight")
                || lower.contains("change flight") || lower.contains("change booking")
                || lower.contains("перенес") || lower.contains("перенести") || lower.contains("перенос")
                || lower.contains("сменить рейс") || lower.contains("изменить рейс") || lower.contains("изменить брон");
        // Heuristic: if a message contains both a booking UUID and a date, treat it as reschedule even without keywords
        boolean uuidAndDate = extractUuid(lower) != null && parseDate(lower) != null;
        // If user already provided booking id earlier and now sends just a date, continue the reschedule flow
        boolean dateOnlyAndPending = (memoryId != null && rescheduleTargetBookingIdBySession.containsKey(memoryId))
                && parseDate(lower) != null;
        return keyword || uuidAndDate || dateOnlyAndPending;
    }

    private boolean isRescheduleConfirmIntent(String lower, String memoryId) {
        if (lower == null) return false;
        boolean hasPending = memoryId != null && rescheduleTargetBookingIdBySession.containsKey(memoryId);
        boolean confirmWords = lower.contains("reschedule it") || lower.contains("confirm reschedule")
                || lower.contains("do reschedule") || lower.contains("перенеси") || lower.contains("подтверди перенос");
        boolean genericConfirm = lower.matches(".*\\b(confirm|ok|okay|yes|да|ок)\\b.*");
        // Treat plain 'reschedule' as confirm when a reschedule flow is pending
        boolean plainKeyword = lower.contains("reschedule") || lower.contains("перенест") || lower.contains("перенос");
        return hasPending && (confirmWords || genericConfirm || plainKeyword);
    }

    private String parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String now = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString();
            String prompt = String.join("\n",
                    "Task: Extract a concrete calendar date from the user's text and normalize it to ISO YYYY-MM-DD.",
                    "Rules:",
                    "- Languages: EN and RU.",
                    "- If the text does not clearly contain a date, do not guess; return hasDate=false and date=null.",
                    "- If the year is missing but a month/day are present, infer the NEAREST FUTURE date relative to 'now'.",
                    "- Do not infer a date from booking contexts, IDs, or general phrases; only from explicit date mentions.",
                    "- Respond with JSON only.",
                    "now: " + now,
                    "text: " + text,
                    "Return JSON schema: {\"hasDate\": true|false, \"date\": \"YYYY-MM-DD\" | null, \"confidence\": number }");
            String json = fallbackLlM.askJson(prompt);
            if (json != null && json.trim().startsWith("{")) {
                java.util.Map<String, Object> map = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>(){});
                Object has = map.get("hasDate");
                boolean hasDate = has instanceof Boolean ? (Boolean) has : Boolean.parseBoolean(String.valueOf(has));
                Object d = map.get("date");
                Object confObj = map.get("confidence");
                double conf = 1.0;
                try { if (confObj != null) conf = Double.parseDouble(String.valueOf(confObj)); } catch (Exception ignore) {}
                if (hasDate && d != null) {
                    String val = String.valueOf(d).trim();
                    if (isIsoDate(val) && conf >= 0.6) return val;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    private boolean isIsoDate(String s) {
        if (s == null || s.length() != 10) return false;
        if (s.charAt(4) != '-' || s.charAt(7) != '-') return false;
        int[] idx = new int[]{0,1,2,3,5,6,8,9};
        for (int i : idx) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private String extractOriginOnly(String text) {
        if (text == null) return null;
        // EN: from CITY / IATA
        java.util.regex.Matcher mEn = java.util.regex.Pattern.compile("(?i)from\\s+([A-Za-z]{3}|[A-Za-z\\-\\s]+?)(?:\\s+(?:on|at|by)\\b|[?.!,]|$)").matcher(text.trim());
        if (mEn.find()) return mEn.group(1).trim();
        // Fallback: single IATA code present
        java.util.regex.Matcher mIata = java.util.regex.Pattern.compile("\\b([A-Z]{3})\\b").matcher(text.trim());
        if (mIata.find()) return mIata.group(1);
        return null;
    }

    private String followupForMissing(String origin, String destination, String date) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (origin == null) missing.add("origin");
        if (destination == null) missing.add("destination");
        if (date == null) missing.add("date (YYYY-MM-DD)");
        String need = String.join(", ", missing);
        String hint = "For example: from SFO to JFK on 2025-12-24";
        return "I need " + need + " to search flights. " + hint + ".";
    }

    private String formatList(java.util.List<java.util.Map<String, Object>> list) {
        return formatList(list, "book");
    }

    private String formatList(java.util.List<java.util.Map<String, Object>> list, String action) {
        String act = (action == null || action.isBlank()) ? "book" : action.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("I found several flights. Here are the options:\n");
        int n = Math.min(list.size(), 5);
        for (int i = 0; i < n; i++) {
            var f = list.get(i);
            sb.append(i + 1).append('.').append(' ')
              .append(String.valueOf(f.getOrDefault("carrier", "?"))).append(' ')
              .append(String.valueOf(f.getOrDefault("flightNumber", "?")))
              .append(" on ").append(String.valueOf(f.getOrDefault("date", "?")))
              .append("  ")
              .append(String.valueOf(f.getOrDefault("origin", "?"))).append(" -> ")
              .append(String.valueOf(f.getOrDefault("destination", "?")))
              .append(", dep ").append(String.valueOf(f.getOrDefault("departure", "?")))
              .append(", arr ").append(String.valueOf(f.getOrDefault("arrival", "?")))
              .append(", price=").append(String.valueOf(f.getOrDefault("price", "?"))).append(' ')
              .append(String.valueOf(f.getOrDefault("currency", "")))
              .append('\n');
        }
        if (list.size() > n) sb.append("(+").append(list.size() - n).append(" more)\n");
        if ("reschedule".equals(act)) {
            sb.append("Which one would you like to reschedule to? You can reply like 'first' or '2nd', then say 'reschedule it'.");
        } else {
            sb.append("Which one would you like to book? You can reply like 'first' or '2nd'.");
        }
        return sb.toString();
    }

    private String formatOne(java.util.Map<String, Object> f) {
        return "Best option: "
                + String.valueOf(f.getOrDefault("carrier", "?")) + ' ' + String.valueOf(f.getOrDefault("flightNumber", "?"))
                + " on " + String.valueOf(f.getOrDefault("date", "?"))
                + "  " + String.valueOf(f.getOrDefault("origin", "?")) + " -> " + String.valueOf(f.getOrDefault("destination", "?"))
                + ", dep " + String.valueOf(f.getOrDefault("departure", "?"))
                + ", arr " + String.valueOf(f.getOrDefault("arrival", "?"))
                + ", price=" + String.valueOf(f.getOrDefault("price", "?")) + ' ' + String.valueOf(f.getOrDefault("currency", ""));
    }

    private String formatSelection(java.util.Map<String, Object> f) {
        return formatSelection(f, "book");
    }

    private String formatSelection(java.util.Map<String, Object> f, String action) {
        String act = (action == null || action.isBlank()) ? "book" : action.toLowerCase(Locale.ROOT);
        String suffix = "book".equals(act) ? "Say 'book it' to confirm." : "Say 'reschedule it' to confirm.";
        return "Selected: "
                + String.valueOf(f.getOrDefault("carrier", "?")) + ' ' + String.valueOf(f.getOrDefault("flightNumber", "?"))
                + " on " + String.valueOf(f.getOrDefault("date", "?"))
                + "  " + String.valueOf(f.getOrDefault("origin", "?")) + " -> " + String.valueOf(f.getOrDefault("destination", "?"))
                + ", dep " + String.valueOf(f.getOrDefault("departure", "?"))
                + ", arr " + String.valueOf(f.getOrDefault("arrival", "?"))
                + ", price=" + String.valueOf(f.getOrDefault("price", "?")) + ' ' + String.valueOf(f.getOrDefault("currency", ""))
                + ". " + suffix;
    }

    // ---- Booking helpers ----
    private boolean isBookingCreateIntent(String lower) {
        if (lower == null) return false;
        // avoid matching 'bookings' listing intent handled elsewhere by requiring a verb form
        boolean hasVerb = lower.contains("book it") || lower.contains("book that") || lower.contains("book this")
                || lower.matches(".*\\bbook(\\s|!|\\.|$).*")
                || lower.contains("reserve");
        boolean cancelWords = lower.contains("cancel") || lower.contains("delete") || lower.contains("отмен");
        return hasVerb && !cancelWords;
    }

    private boolean isCancelBookingIntent(String lower) {
        if (lower == null) return false;
        return lower.contains("cancel booking") || lower.contains("delete booking")
                || lower.matches(".*\\bcancel\\b.*")
                || lower.contains("отмени брон") || lower.contains("удали брон");
    }

    private String extractUuid(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b").matcher(text);
        if (m.find()) return m.group();
        return null;
    }

    private String buildTripId(java.util.Map<String, Object> f) {
        if (f == null) return "unknown";
        String carrier = String.valueOf(f.getOrDefault("carrier", ""));
        String flight = String.valueOf(f.getOrDefault("flightNumber", ""));
        String date = String.valueOf(f.getOrDefault("date", ""));
        if (carrier == null) carrier = "";
        carrier = carrier.replace(" ", "");
        return carrier + "-" + flight + "-" + date;
    }

    // ---- Tool response helpers ----
    private String extractBookingIdFromResponse(String resp) {
        if (resp == null || resp.isBlank()) return null;
        try {
            if (!resp.trim().startsWith("{")) return null;
            java.util.Map<String, Object> m = mapper.readValue(resp, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){ });
            Object direct = m.get("bookingId");
            if (direct != null) return String.valueOf(direct);
            Object data = m.get("data");
            if (data instanceof java.util.Map<?,?> dm) {
                Object id = dm.get("bookingId");
                if (id != null) return String.valueOf(id);
            }
        } catch (Exception ignore) {}
        return null;
    }

    private boolean isToolOk(String resp) {
        if (resp == null || resp.isBlank()) return false;
        try {
            if (resp.trim().startsWith("{")) {
                java.util.Map<String, Object> m = mapper.readValue(resp, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){ });
                Object st = m.get("status");
                Object hs = m.get("httpStatus");
                boolean okStatus = st != null && "OK".equalsIgnoreCase(String.valueOf(st));
                int code = -1;
                if (hs instanceof Number n) code = n.intValue();
                else if (hs != null) { try { code = Integer.parseInt(String.valueOf(hs)); } catch (Exception ignore) {} }
                return okStatus && (code < 0 || code < 400);
            }
            // Fallback heuristic
            return resp.toLowerCase(java.util.Locale.ROOT).contains("deleted");
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Unwrap helpers for standardized tool responses ----
    private java.util.List<java.util.Map<String, Object>> unwrapDataList(String json) throws Exception {
        if (json == null || json.isBlank()) return java.util.List.of();
        String t = json.trim();
        if (t.startsWith("{")) {
            java.util.Map<String, Object> m = mapper.readValue(t, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){ });
            Object data = m.get("data");
            if (data instanceof java.util.List<?> lst) {
                java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
                for (Object o : lst) {
                    if (o instanceof java.util.Map<?,?> mm) {
                        out.add((java.util.Map<String, Object>) mm);
                    }
                }
                return out;
            }
            if (data instanceof java.util.Map<?,?> mm) {
                return java.util.List.of((java.util.Map<String, Object>) mm);
            }
            return java.util.List.of();
        }
        // Backward compatibility: plain array
        if (t.startsWith("[")) {
            return mapper.readValue(t, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>(){ });
        }
        return java.util.List.of();
    }

    private java.util.Map<String, Object> unwrapDataObject(String json) throws Exception {
        if (json == null || json.isBlank()) return java.util.Map.of();
        String t = json.trim();
        if (t.startsWith("{")) {
            java.util.Map<String, Object> m = mapper.readValue(t, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){ });
            Object data = m.get("data");
            if (data instanceof java.util.Map<?,?> mm) {
                return (java.util.Map<String, Object>) mm;
            }
            // Backward compatibility: maybe the whole object is the flight
            if (m.containsKey("carrier") || m.containsKey("flightNumber")) {
                return m;
            }
        }
        return java.util.Map.of();
    }

}
