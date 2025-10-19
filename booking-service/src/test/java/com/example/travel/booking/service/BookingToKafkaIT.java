package com.example.travel.booking.service;

import com.example.travel.common.Topics;
import com.example.travel.common.events.BookingCreatedEvent;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingToKafkaIT {

    @LocalServerPort int port;

    static final String BOOTSTRAP = Optional.ofNullable(System.getenv("KAFKA_BOOTSTRAP_SERVERS"))
            .filter(s -> !s.isBlank())
            .orElse("127.0.0.1:9092");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", () -> BOOTSTRAP);
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:bookingdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.kafka.consumer.properties.spring.json.trusted.packages",
                () -> "com.example.travel.common.events");
    }

    private static void ensureTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP))) {
            admin.createTopics(List.of(
                    new NewTopic(Topics.BOOKINGS, 1, (short) 1)
            )).all().get(5, TimeUnit.SECONDS);
        } catch (Exception ignore) {

        }
    }

    private KafkaConsumer<String, BookingCreatedEvent> consumer() throws Exception {
        ensureTopics();
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "booking-it-" + UUID.randomUUID());
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        p.put(JsonDeserializer.VALUE_DEFAULT_TYPE, BookingCreatedEvent.class.getName());
        p.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.travel.common.events");
        return new KafkaConsumer<>(p);
    }

    @AfterEach
    void resetRestAssured() { RestAssured.reset(); }

    @Test
    void whenCreateBooking_byHttp_thenEventOnLocalKafka() throws Exception {
        var c = consumer();
        try {
            c.subscribe(List.of(Topics.BOOKINGS));

            String base = "http://localhost:" + port;
            String userId = "u400";
            String tripId = "trip-abc";
            double price = 88.8;

            Response resp =
                    given().contentType("application/json")
                            .body("""
                             {"userId":"%s","tripId":"%s","price":%s}
                             """.formatted(userId, tripId, price))
                            .when().post(base + "/api/bookings")
                            .then().statusCode(anyOf(is(200), is(201), is(202)))
                            .extract().response();

            boolean matched = false;
            long deadline = System.currentTimeMillis() + 10_000;
            while (!matched && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, BookingCreatedEvent> recs = c.poll(Duration.ofMillis(200));
                for (var r : recs.records(Topics.BOOKINGS)) {
                    var e = r.value();
                    if (e != null && userId.equals(e.getUserId()) && tripId.equals(e.getTripId())) {
                        matched = true;
                        break;
                    }
                }
            }

            assertThat(matched)
                    .as("should receive BookingCreatedEvent{userId=%s, tripId=%s} on topic %s", userId, tripId, Topics.BOOKINGS)
                    .isTrue();
        } finally {
            c.close();
        }
    }
}
