package com.example.travel.payment.web;

import com.example.travel.payment.domain.Payment;
import com.example.travel.payment.dto.PaymentRequest;
import com.example.travel.payment.repository.PaymentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository repository;

    public PaymentController(PaymentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Payment> list() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PaymentRequest req) {
        if (req.getBookingId() == null || req.getBookingId().isBlank()) {
            return ResponseEntity.badRequest().body("bookingId is required");
        }
        if (repository.existsByBookingId(req.getBookingId())) {
            return ResponseEntity.badRequest().body("payment for booking already exists");
        }
        Payment p = new Payment();
        p.setBookingId(req.getBookingId());
        p.setUserId(req.getUserId());
        p.setAmount(req.getAmount());
        Payment saved = repository.save(p);
        return ResponseEntity.created(URI.create("/api/payments/" + saved.getId())).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
