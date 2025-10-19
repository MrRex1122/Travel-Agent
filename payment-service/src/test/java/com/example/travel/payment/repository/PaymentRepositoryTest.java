package com.example.travel.payment.repository;

import com.example.travel.payment.domain.Payment;
import com.example.travel.payment.domain.PaymentStatus;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

@DataJpaTest
class PaymentRepositoryTest {

    @Resource
    private PaymentRepository paymentRepository;

    @Test
    void create() {
        Payment p = new Payment();
        p.setBookingId("b-3001");
        p.setUserId("u-3001");
        p.setAmount(199.00);
        p.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(p);
        System.out.println("Saved payment: " + saved);
    }

    @Test
    void readById() {
        Payment p = new Payment();
        p.setBookingId("b-3002");
        p.setUserId("u-3002");
        p.setAmount(299.00);
        p.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(p);
        Optional<Payment> found = paymentRepository.findById(saved.getId());
        System.out.println("Found by id: " + found);
    }

    @Test
    void readByBookingId() {
        Payment p = new Payment();
        p.setBookingId("b-3003");
        p.setUserId("u-3003");
        p.setAmount(9.99);
        p.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(p);
        Optional<Payment> byBookingId = paymentRepository.findByBookingId("b-3003");
        System.out.println("Found by bookingId: " + byBookingId);
    }

    @Test
    void update() {
        Payment p = new Payment();
        p.setBookingId("b-3004");
        p.setUserId("u-3004");
        p.setAmount(1.00);
        p.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(p);

        saved.setStatus(PaymentStatus.CAPTURED);
        saved.setAmount(1.23);
        Payment updated = paymentRepository.save(saved);
        System.out.println("Updated payment: " + updated);
    }

    @Test
    void deleteById() {
        Payment p = new Payment();
        p.setBookingId("b-3005");
        p.setUserId("u-3005");
        p.setAmount(5.00);
        p.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(p);
        UUID id = saved.getId();

        paymentRepository.deleteById(id);
        Optional<Payment> after = paymentRepository.findById(id);
        System.out.println("Deleted id: " + id + ", find after delete: " + after);
    }
}
