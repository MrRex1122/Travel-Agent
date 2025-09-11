package com.example.travel.payment.messaging;

import com.example.travel.common.Event;
import com.example.travel.common.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentListener {

    @KafkaListener(topics = Topics.BOOKINGS, groupId = "${spring.kafka.consumer.group-id:payment-service}")
    public void onBookingCreated(Event event) {
        // Простая заглушка обработки платежа
        System.out.println("[payment-service] Received event: type=" + event.getType() + ", payload=" + event.getPayload());
        // Здесь могла бы быть логика проверки/инициации платежа
    }
}
