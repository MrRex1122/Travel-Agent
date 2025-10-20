package com.example.travel.payment.service;

import com.example.travel.payment.domain.Payment;
import com.example.travel.payment.domain.PaymentStatus;
import com.example.travel.common.events.BookingCreatedEvent;
import com.example.travel.payment.messaging.PaymentEventProducer;
import com.example.travel.payment.messaging.PaymentOutcomeEvent;
import com.example.travel.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentProcessor {
    private final PaymentRepository repo;
    private final PaymentEventProducer producer;

    public PaymentProcessor(PaymentRepository repo, PaymentEventProducer producer) {
        this.repo = repo;
        this.producer = producer;
    }

    @Transactional
    public Payment handle(BookingCreatedEvent evt) {
        Payment existing = repo.findByBookingId(evt.getBookingId()).orElse(null);
        if (existing != null) return existing;

        Payment p = new Payment();
        p.setBookingId(evt.getBookingId());
        p.setUserId(evt.getUserId());
        p.setAmount(BigDecimal.valueOf(evt.getPrice()));
        p.setStatus(PaymentStatus.PENDING);

        boolean ok = ThreadLocalRandom.current().nextDouble() > 0.1; // 90% 成功
        if (ok) {
            p.setStatus(PaymentStatus.AUTHORIZED);
            p.setProviderTxnId("PROV-" + System.currentTimeMillis());
        } else {
            p.setStatus(PaymentStatus.FAILED);
        }

        Payment saved = repo.save(p);

        if (producer != null) {
            String outcome = saved.getStatus().name();
            producer.publish(new PaymentOutcomeEvent(
                    saved.getId(), saved.getBookingId(), saved.getUserId(), outcome, saved.getAmount()
            ));
        }
        return saved;
    }
}
