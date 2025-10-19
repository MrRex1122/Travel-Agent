package com.example.travel.payment.service;

import com.example.travel.common.Topics;
import com.example.travel.common.events.BookingCreatedEvent;
import com.example.travel.payment.domain.Payment;
import com.example.travel.payment.domain.PaymentStatus;
import com.example.travel.payment.messaging.PaymentOutcomeEvent;
import com.example.travel.payment.repository.PaymentRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest
class PaymentFlowIT {

    @Container
    static KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.datasource.url",
                () -> "jdbc:h2:mem:paymentdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.kafka.consumer.properties.spring.json.trusted.packages",
                () -> "com.example.travel.common.events");
    }

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired PaymentRepository paymentRepository;

    @BeforeEach
    void clean() { paymentRepository.deleteAll(); }

    @Test
    void whenBookingCreatedEvent_thenPaymentIsPersisted() {
        String bookingId = UUID.randomUUID().toString();
        kafkaTemplate.send(Topics.BOOKINGS,
                bookingId, new BookingCreatedEvent(bookingId, "u300", "trip-xyz", 199.0));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(paymentRepository.findByBookingId(bookingId)).isPresent()
                );

        Payment p = paymentRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(p.getUserId()).isEqualTo("u300");
        assertThat(p.getAmount()).isEqualTo(199.0);
        assertThat(p.getStatus()).isIn(PaymentStatus.values());
    }

    @Test
    void whenBookingCreatedEvent_thenPaymentOutcomeEventIsPublished() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-it-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentOutcomeEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, PaymentOutcomeEvent>(props);
        consumer.subscribe(List.of(com.example.travel.payment.messaging.Topics.PAYMENTS));

        String bookingId = UUID.randomUUID().toString();
        kafkaTemplate.send(Topics.BOOKINGS,
                bookingId, new BookingCreatedEvent(bookingId, "u301", "trip-123", 299.0));

        var records = consumer.poll(Duration.ofSeconds(5));
        consumer.close();

        boolean got = false;
        for (var r : records.records(com.example.travel.payment.messaging.Topics.PAYMENTS)) {
            var e = r.value();
            if (e != null && bookingId.equals(e.getBookingId())) {
                got = true;
                break;
            }
        }
        assertThat(got).isTrue();
    }
}
