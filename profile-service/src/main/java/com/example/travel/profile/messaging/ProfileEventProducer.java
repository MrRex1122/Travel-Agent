package com.example.travel.profile.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProfileEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public ProfileEventProducer(KafkaTemplate<String, Object> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }
    public void publish(ProfileUpdatedEvent evt){
        String key = evt.getUserId();
        kafkaTemplate.send(Topics.PROFILES, key, evt);
    }
}