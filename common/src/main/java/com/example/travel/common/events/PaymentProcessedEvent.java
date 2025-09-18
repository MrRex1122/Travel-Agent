package com.example.travel.common.events;

public class PaymentProcessedEvent {
    private String paymentId;
    private String bookingId;
    private String status; // e.g., AUTHORIZED, FAILED
    private String reason; // optional failure reason
    private double amount;

    public PaymentProcessedEvent() {}

    public PaymentProcessedEvent(String paymentId, String bookingId, String status, String reason, double amount) {
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.status = status;
        this.reason = reason;
        this.amount = amount;
    }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
