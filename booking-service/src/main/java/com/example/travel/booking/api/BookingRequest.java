package com.example.travel.booking.api;

public class BookingRequest {
    private String userId;
    private String tripId;
    private double price;

    public BookingRequest() {}

    public BookingRequest(String userId, String tripId, double price) {
        this.userId = userId;
        this.tripId = tripId;
        this.price = price;
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
