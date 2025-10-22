package com.example.travel.assistant.tools;

import com.example.travel.assistant.memory.ConversationContext;
import com.example.travel.assistant.memory.SharedChatMemoryProvider;
import com.example.travel.assistant.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BookingTools {

    private final WebClient webClient;
    private final SharedChatMemoryProvider memoryProvider;
    private final ObjectProvider<AgentService> agentServiceProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    private final long timeoutMs;
    private final int retries;

    // Lightweight in-code circuit breaker (no external deps)
    private final java.util.concurrent.atomic.AtomicInteger consecutiveFailures = new java.util.concurrent.atomic.AtomicInteger(0);
    private volatile long circuitOpenUntil = 0L;
    private static final int FAILURE_THRESHOLD = 3;
    private static final long OPEN_DURATION_MS = 10_000L;

    public BookingTools(@Value("${assistant.tools.booking.base-url:${BOOKING_BASE_URL:http://localhost:18081}}") String baseUrl,
                        @Value("${assistant.tools.booking.timeout-ms:${BOOKING_TIMEOUT_MS:5000}}") long timeoutMs,
                        @Value("${assistant.tools.booking.retries:${BOOKING_RETRIES:2}}") int retries,
                        SharedChatMemoryProvider memoryProvider,
                        ObjectProvider<AgentService> agentServiceProvider) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.retries = Math.max(0, retries);
        this.memoryProvider = memoryProvider;
        this.agentServiceProvider = agentServiceProvider;
    }

    private boolean isCircuitOpen() {
        long now = System.currentTimeMillis();
        return now < circuitOpenUntil;
    }

    private void recordFailure() {
        int c = consecutiveFailures.incrementAndGet();
        if (c >= FAILURE_THRESHOLD) {
            circuitOpenUntil = System.currentTimeMillis() + OPEN_DURATION_MS;
        }
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        circuitOpenUntil = 0L;
    }

    private String wrap(String status, int httpStatus, String message, String body) {
        Object data = null;
        if (body != null && !body.isBlank()) {
            try {
                data = mapper.readValue(body, Object.class);
            } catch (Exception ignore) {
                data = body;
            }
        }
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("status", status);
        out.put("httpStatus", httpStatus);
        if (message != null) out.put("message", message);
        if (data != null && "OK".equalsIgnoreCase(status)) out.put("data", data);
        if (!"OK".equalsIgnoreCase(status)) {
            java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("code", httpStatus);
            if (message != null) err.put("message", message);
            out.put("error", err);
            // Still include raw body in data for backward compatibility if present
            if (data != null) out.put("data", data);
        }
        try { return mapper.writeValueAsString(out); } catch (Exception e) { return "{\"status\":\""+status+"\",\"httpStatus\":"+httpStatus+"}"; }
    }

    @Tool("Create a booking with given userId, tripId and price. Returns a structured JSON with status and httpStatus.")
    public String createBooking(String userId, String tripId, double price) {
        if (isCircuitOpen()) {
            return wrap("ERROR", 503, "CIRCUIT_OPEN", null);
        }
        Exception lastEx = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                var payload = java.util.Map.of(
                        "userId", userId,
                        "tripId", tripId,
                        "price", price
                );
                var entity = webClient.post()
                        .uri("/api/bookings")
                        .header("Idempotency-Key", userId + ":" + tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(payload))
                        .exchangeToMono(resp -> resp.toEntity(String.class))
                        .timeout(java.time.Duration.ofMillis(timeoutMs))
                        .block();
                int code = entity != null ? entity.getStatusCode().value() : 0;
                String body = entity != null ? entity.getBody() : null;
                if (code >= 200 && code < 300) {
                    recordSuccess();
                    // Remember last booking id in session and add note
                    try {
                        String memId = ConversationContext.getMemoryId();
                        if (memId != null && body != null && !body.isBlank()) {
                            var map = mapper.readValue(body, java.util.Map.class);
                            Object bid = map instanceof java.util.Map ? ((java.util.Map<?,?>) map).get("bookingId") : null;
                            if (bid != null) {
                                AgentService svc = agentServiceProvider.getIfAvailable();
                                if (svc != null) svc.rememberLastBooking(memId, String.valueOf(bid));
                            }
                            var memory = memoryProvider.get(memId);
                            if (memory != null) {
                                memory.add(AiMessage.from("Booking created (user=" + userId + ") trip=" + tripId + ", price=" + price + "."));
                            }
                        }
                    } catch (Exception ignore) {}
                    return wrap("OK", code, null, body);
                } else {
                    recordFailure();
                    return wrap("ERROR", code, null, body);
                }
            } catch (Exception ex) {
                lastEx = ex;
                recordFailure();
                if (attempt == retries) {
                    return wrap("ERROR", 599, ex.getMessage(), null);
                }
            }
        }
        return wrap("ERROR", 599, lastEx != null ? lastEx.getMessage() : "Unknown error", null);
    }

    @Tool("List existing bookings. Returns a JSON array as text.")
    public String listBookings() {
        if (isCircuitOpen()) {
            return "[]";
        }
        Exception lastEx = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                var resp = webClient.get()
                        .uri("/api/bookings")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(java.time.Duration.ofMillis(timeoutMs))
                        .block();
                recordSuccess();
                return resp != null ? resp : "[]";
            } catch (Exception ex) {
                lastEx = ex;
                recordFailure();
                if (attempt == retries) {
                    return "[]";
                }
            }
        }
        return "[]";
    }

    @Tool("Get booking details by its ID. Returns booking JSON as text or a not-found message.")
    public String getBooking(String bookingId) {
        if (isCircuitOpen()) {
            return "{}";
        }
        Exception lastEx = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                var resp = webClient.get()
                        .uri("/api/bookings/{id}", bookingId)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(java.time.Duration.ofMillis(timeoutMs))
                        .block();
                recordSuccess();
                return resp != null ? resp : "{}";
            } catch (Exception ex) {
                lastEx = ex;
                recordFailure();
                if (attempt == retries) {
                    return "{}";
                }
            }
        }
        return "{}";
    }

    @Tool("Update an existing booking by ID with new userId, tripId and price. Returns structured JSON with status and httpStatus.")
    public String updateBooking(String bookingId, String userId, String tripId, double price) {
        if (isCircuitOpen()) {
            return wrap("ERROR", 503, "CIRCUIT_OPEN", null);
        }
        Exception lastEx = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                var payload = java.util.Map.of(
                        "userId", userId,
                        "tripId", tripId,
                        "price", price
                );
                var entity = webClient.put()
                        .uri("/api/bookings/{id}", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(payload))
                        .exchangeToMono(resp -> resp.toEntity(String.class))
                        .timeout(java.time.Duration.ofMillis(timeoutMs))
                        .block();
                int code = entity != null ? entity.getStatusCode().value() : 0;
                String body = entity != null ? entity.getBody() : null;
                if (code >= 200 && code < 300) {
                    recordSuccess();
                    return wrap("OK", code, null, body);
                } else {
                    recordFailure();
                    return wrap("ERROR", code, null, body);
                }
            } catch (Exception ex) {
                lastEx = ex;
                recordFailure();
                if (attempt == retries) {
                    return wrap("ERROR", 599, ex.getMessage(), null);
                }
            }
        }
        return wrap("ERROR", 599, lastEx != null ? lastEx.getMessage() : "Unknown error", null);
    }

    @Tool("Delete a booking by ID. Returns structured JSON with status and httpStatus.")
    public String deleteBooking(String bookingId) {
        if (isCircuitOpen()) {
            return wrap("ERROR", 503, "CIRCUIT_OPEN", null);
        }
        Exception lastEx = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                var entity = webClient.delete()
                        .uri("/api/bookings/{id}", bookingId)
                        .exchangeToMono(resp -> resp.toBodilessEntity())
                        .timeout(java.time.Duration.ofMillis(timeoutMs))
                        .block();
                int code = entity != null ? entity.getStatusCode().value() : 0;
                if (code >= 200 && code < 300) {
                    recordSuccess();
                    return wrap("OK", code, "Booking deleted: " + bookingId, null);
                } else {
                    recordFailure();
                    return wrap("ERROR", code, null, null);
                }
            } catch (Exception ex) {
                lastEx = ex;
                recordFailure();
                if (attempt == retries) {
                    return wrap("ERROR", 599, ex.getMessage(), null);
                }
            }
        }
        return wrap("ERROR", 599, lastEx != null ? lastEx.getMessage() : "Unknown error", null);
    }
}
