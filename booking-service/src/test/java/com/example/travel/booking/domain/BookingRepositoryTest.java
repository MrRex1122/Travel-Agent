package com.example.travel.booking.domain;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

@DataJpaTest
class BookingRepositoryTest {

    @Resource
    private BookingRepository bookingRepository;

    @Test
    void create() {
        Booking b = new Booking();
        b.setUserId("u-2001");
        b.setTripId("t-001");
        b.setPrice(99.00);
        Booking saved = bookingRepository.save(b);
        System.out.println("Saved booking: " + saved);
    }

    @Test
    void readById() {
        Booking b = new Booking();
        b.setUserId("u-2002");
        b.setTripId("t-002");
        b.setPrice(199.00);
        Booking saved = bookingRepository.save(b);
        Optional<Booking> found = bookingRepository.findById(saved.getId());
        System.out.println("Found by id: " + found);
    }

    @Test
    void update() {
        Booking b = new Booking();
        b.setUserId("u-2003");
        b.setTripId("t-003");
        b.setPrice(10.00);
        Booking saved = bookingRepository.save(b);

        saved.setPrice(12.34);
        Booking updated = bookingRepository.save(saved);
        System.out.println("Updated booking: " + updated);
    }

    @Test
    void deleteById() {
        Booking b = new Booking();
        b.setUserId("u-2004");
        b.setTripId("t-004");
        b.setPrice(50.00);
        Booking saved = bookingRepository.save(b);
        UUID id = saved.getId();

        bookingRepository.deleteById(id);
        Optional<Booking> after = bookingRepository.findById(id);
        System.out.println("Deleted id: " + id + ", find after delete: " + after);
    }
}
