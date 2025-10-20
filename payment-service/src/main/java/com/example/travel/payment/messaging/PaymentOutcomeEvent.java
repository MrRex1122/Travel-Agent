package com.example.travel.payment.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentOutcomeEvent {
    private UUID paymentId;
    private String bookingId;
    private String userId;
    private String outcome;
    private BigDecimal amount;
    private Instant occurredAt = Instant.now();

    public PaymentOutcomeEvent() {
    }

    public PaymentOutcomeEvent(UUID paymentId, String bookingId, String userId,
                               String outcome, BigDecimal amount) {
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.userId = userId;
        this.outcome = outcome;
        this.amount = amount;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}

