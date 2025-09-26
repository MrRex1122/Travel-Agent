package com.example.travel.payment.repository;

import com.example.travel.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(String bookingId);
    boolean existsByBookingId(String bookingId);
}
