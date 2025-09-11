package com.example.travel.common;

public class Event {
    private String type;
    private String payload;

    public Event() {}
    public Event(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
