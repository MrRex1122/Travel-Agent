package com.example.travel.booking.config;

import com.example.travel.booking.domain.Booking;
import com.example.travel.booking.domain.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedBookings(BookingRepository repo) {
        return args -> seed(repo);
    }

    @Transactional
    void seed(BookingRepository repo) {
        long count = repo.count();
        if (count > 0) {
            log.info("[booking-service] Skipping seed: {} bookings already present", count);
            return;
        }
        List<Booking> demo = List.of(
                new Booking("u-100", "t-nyc-001", 199.0),
                new Booking("u-101", "t-sfo-002", 349.5),
                new Booking("u-102", "t-jfk-003", 129.9),
                new Booking("u-103", "t-ber-004", 499.0),
                new Booking("u-104", "t-tok-005", 799.0)
        );
        repo.saveAll(demo);
        log.info("[booking-service] Seeded {} demo bookings", demo.size());
    }
}
