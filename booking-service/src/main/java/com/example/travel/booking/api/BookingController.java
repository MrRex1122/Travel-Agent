package com.example.travel.booking.api;

import com.example.travel.booking.messaging.BookingEventProducer;
import com.example.travel.common.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingEventProducer producer;
    private final ObjectMapper objectMapper;

    public BookingController(BookingEventProducer producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BookingRequest request) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of(
                "userId", request.getUserId(),
                "tripId", request.getTripId(),
                "price", request.getPrice()
        ));
        Event event = new Event("BOOKING_CREATED", payload);
        String key = request.getUserId() + ":" + request.getTripId();
        producer.publish(event, key);
        return ResponseEntity.accepted().body(Map.of(
                "status", "PUBLISHED",
                "topic", "travel.bookings",
                "key", key
        ));
    }
}
