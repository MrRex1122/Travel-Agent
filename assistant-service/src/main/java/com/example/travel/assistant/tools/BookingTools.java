package com.example.travel.assistant.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BookingTools {

    private final WebClient webClient;

    public BookingTools(@Value("${assistant.tools.booking.base-url:${BOOKING_BASE_URL:http://localhost:18081}}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Tool("Create a booking with given userId, tripId and price. Returns a short status message.")
    public String createBooking(String userId, String tripId, double price) {
        try {
            var payload = java.util.Map.of(
                    "userId", userId,
                    "tripId", tripId,
                    "price", price
            );
            var resp = webClient.post()
                    .uri("/api/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(payload))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return resp != null ? resp : "No response from booking-service";
        } catch (Exception ex) {
            return "Failed to create booking: " + ex.getMessage();
        }
    }

    @Tool("List existing bookings. Returns a JSON array as text.")
    public String listBookings() {
        try {
            var resp = webClient.get()
                    .uri("/api/bookings")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return resp != null ? resp : "[]";
        } catch (Exception ex) {
            return "Failed to list bookings: " + ex.getMessage();
        }
    }

    @Tool("Get booking details by its ID. Returns booking JSON as text or a not-found message.")
    public String getBooking(String bookingId) {
        try {
            var resp = webClient.get()
                    .uri("/api/bookings/{id}", bookingId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return resp != null ? resp : "{}";
        } catch (Exception ex) {
            return "Failed to get booking: " + ex.getMessage();
        }
    }

    @Tool("Update an existing booking by ID with new userId, tripId and price. Returns the updated booking JSON as text.")
    public String updateBooking(String bookingId, String userId, String tripId, double price) {
        try {
            var payload = java.util.Map.of(
                    "userId", userId,
                    "tripId", tripId,
                    "price", price
            );
            var resp = webClient.put()
                    .uri("/api/bookings/{id}", bookingId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(payload))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return resp != null ? resp : "{}";
        } catch (Exception ex) {
            return "Failed to update booking: " + ex.getMessage();
        }
    }

    @Tool("Delete a booking by ID. Returns a short status message.")
    public String deleteBooking(String bookingId) {
        try {
            webClient.delete()
                    .uri("/api/bookings/{id}", bookingId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return "Booking deleted: " + bookingId;
        } catch (Exception ex) {
            return "Failed to delete booking: " + ex.getMessage();
        }
    }
}
