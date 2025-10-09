package com.example.travel.profile.messaging;

import java.time.Instant;
import java.util.UUID;

public class ProfileUpdatedEvent {
    private UUID profileId;
    private String userId;
    private String changeType;
    private Instant occurredAt = Instant.now();

    public ProfileUpdatedEvent() {
    }

    public ProfileUpdatedEvent(UUID profileId, String userId, String changeType) {
        this.profileId = profileId;
        this.userId = userId;
        this.changeType = changeType;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
