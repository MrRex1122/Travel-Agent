package com.example.travel.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple in-memory booking storage and tools.
 *
 * All data is ephemeral (lives in-memory inside assistant-service only) and is
 * suitable for local demos when external tool-calling is not available.
 */
@Component
public class InMemoryBookingTool {

    private static final Logger log = LoggerFactory.getLogger(InMemoryBookingTool.class);

    private final ObjectMapper mapper = new ObjectMapper();

    // userId -> list of bookings
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<BookingRec>> store = new ConcurrentHashMap<>();

    public static final class BookingRec {
        public final String id;
        public final String userId;
        public final String tripId;
        public final double price;
        public volatile String status; // ACTIVE | CANCELLED
        public final OffsetDateTime createdAt;

        public BookingRec(String userId, String tripId, double price) {
            this.id = UUID.randomUUID().toString();
            this.userId = userId;
            this.tripId = tripId;
            this.price = price;
            this.status = "ACTIVE";
            this.createdAt = OffsetDateTime.now();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("userId", userId);
            m.put("tripId", tripId);
            m.put("price", price);
            m.put("status", status);
            m.put("createdAt", createdAt.toString());
            return m;
        }
    }

    @Tool("Register a new in-memory booking for the given userId, tripId and price. Returns booking JSON as text.")
    public String registerBooking(String userId, String tripId, double price) {
        try {
            if (userId == null || userId.isBlank()) return "userId is required";
            if (tripId == null || tripId.isBlank()) return "tripId is required";
            if (price < 0) return "price must be >= 0";

            BookingRec rec = new BookingRec(userId, tripId, price);
            store.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(rec);
            return mapper.writeValueAsString(rec.toMap());
        } catch (Exception e) {
            log.warn("[InMemoryBookingTool] registerBooking failed: {}", e.toString());
            return "Failed to register booking: " + e.getMessage();
        }
    }

    @Tool("List in-memory bookings for a userId. Returns a JSON array as text.")
    public String listUserBookings(String userId) {
        try {
            if (userId == null || userId.isBlank()) return "[]";
            List<BookingRec> list = store.getOrDefault(userId, new CopyOnWriteArrayList<>());
            List<Map<String, Object>> out = new ArrayList<>();
            for (BookingRec b : list) out.add(b.toMap());
            // sort by createdAt desc
            out.sort((a, b) -> String.valueOf(b.get("createdAt")).compareTo(String.valueOf(a.get("createdAt"))));
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("[InMemoryBookingTool] listUserBookings failed: {}", e.toString());
            return "[]";
        }
    }

    @Tool("Cancel an in-memory booking by its id. Returns the updated booking JSON as text or a not-found message.")
    public String cancelBooking(String bookingId) {
        try {
            if (bookingId == null || bookingId.isBlank()) return "bookingId is required";
            for (CopyOnWriteArrayList<BookingRec> list : store.values()) {
                for (BookingRec rec : list) {
                    if (bookingId.equalsIgnoreCase(rec.id)) {
                        rec.status = "CANCELLED";
                        return mapper.writeValueAsString(rec.toMap());
                    }
                }
            }
            return "Booking not found: " + bookingId;
        } catch (Exception e) {
            log.warn("[InMemoryBookingTool] cancelBooking failed: {}", e.toString());
            return "Failed to cancel booking: " + e.getMessage();
        }
    }
}
