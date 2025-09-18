package com.example.travel.common.events;

public class ProfileUpdatedEvent {
    private String userId;
    private String changeType; // e.g., CONTACT_UPDATED, PREFERENCES_UPDATED
    private long updatedAtEpochMillis;

    public ProfileUpdatedEvent() {}

    public ProfileUpdatedEvent(String userId, String changeType, long updatedAtEpochMillis) {
        this.userId = userId;
        this.changeType = changeType;
        this.updatedAtEpochMillis = updatedAtEpochMillis;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public long getUpdatedAtEpochMillis() { return updatedAtEpochMillis; }
    public void setUpdatedAtEpochMillis(long updatedAtEpochMillis) { this.updatedAtEpochMillis = updatedAtEpochMillis; }
}
