package com.example.travel.common.events;

public class BookingCreatedEvent {
    private String bookingId; // optional for future use
    private String userId;
    private String tripId;
    private double price;

    public BookingCreatedEvent() {}

    public BookingCreatedEvent(String bookingId, String userId, String tripId, double price) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.tripId = tripId;
        this.price = price;
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

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
