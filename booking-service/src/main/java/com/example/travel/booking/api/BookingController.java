package com.example.travel.booking.api;

import com.example.travel.booking.domain.Booking;
import com.example.travel.booking.domain.BookingRepository;
import com.example.travel.booking.messaging.BookingEventProducer;
import com.example.travel.common.Topics;
import com.example.travel.common.events.BookingCreatedEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingRepository repository;
    private final BookingEventProducer producer;

    public BookingController(BookingRepository repository, BookingEventProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BookingRequest request) {
        Booking toSave = new Booking(request.getUserId(), request.getTripId(), request.getPrice());
        Booking saved = repository.save(toSave);

        BookingCreatedEvent event = new BookingCreatedEvent(
                saved.getId() != null ? saved.getId().toString() : null,
                saved.getUserId(),
                saved.getTripId(),
                saved.getPrice()
        );
        String key = saved.getUserId() + ":" + saved.getTripId();
        producer.publish(event, key);

        return ResponseEntity.accepted().body(Map.of(
                "status", "PUBLISHED",
                "topic", Topics.BOOKINGS,
                "key", key,
                "bookingId", saved.getId() != null ? saved.getId().toString() : null
        ));
    }

    @GetMapping
    public ResponseEntity<List<Booking>> list() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getById(@PathVariable UUID id) {
        Optional<Booking> booking = repository.findById(id);
        return booking.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        
    }

    @PutMapping("/{id}")
    public ResponseEntity<Booking> update(@PathVariable UUID id, @RequestBody BookingRequest request) {
        Optional<Booking> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Booking existing = existingOpt.get();
        existing.setUserId(request.getUserId());
        existing.setTripId(request.getTripId());
        existing.setPrice(request.getPrice());
        Booking updated = repository.save(existing);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
