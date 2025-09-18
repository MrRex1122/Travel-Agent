package com.example.travel.payment.messaging;

import com.example.travel.common.Topics;
import com.example.travel.common.events.BookingCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentListener {

    @KafkaListener(topics = Topics.BOOKINGS, groupId = "${spring.kafka.consumer.group-id:payment-service}")
    public void onBookingCreated(BookingCreatedEvent event) {
        System.out.println("[payment-service] BookingCreated: userId=" + event.getUserId() +
                ", tripId=" + event.getTripId() + ", price=" + event.getPrice());
    }
}
