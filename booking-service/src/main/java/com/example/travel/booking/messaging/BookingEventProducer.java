package com.example.travel.booking.messaging;

import com.example.travel.common.Event;
import com.example.travel.common.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(Event event, String key) {
        kafkaTemplate.send(Topics.BOOKINGS, key, event);
    }
}
