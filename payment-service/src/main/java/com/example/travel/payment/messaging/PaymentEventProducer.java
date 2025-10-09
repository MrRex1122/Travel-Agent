package com.example.travel.payment.messaging;

import com.example.travel.common.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PaymentOutcomeEvent event) {
        String key = event.getBookingId();
        kafkaTemplate.send(Topics.PAYMENTS, key, event);
    }
}
