package com.example.travel.profile.repository;

import com.example.travel.profile.domain.UserProfile;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

@DataJpaTest
class UserProfileRepositoryTest {

    @Resource
    private UserProfileRepository userProfileRepository;

    @Test
    void create() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId("u-1001");
        userProfile.setEmail("u1001@example.com");
        userProfile.setName("Alice");
        UserProfile saved = userProfileRepository.save(userProfile);
        System.out.println("Saved user profile: " + saved);
    }

    @Test
    void readById() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId("u-1002");
        userProfile.setEmail("u1002@example.com");
        userProfile.setName("Bob");
        UserProfile saved = userProfileRepository.save(userProfile);
        Optional<UserProfile> found = userProfileRepository.findById(saved.getId());
        System.out.println("Found by id: " + found);
    }

    @Test
    void readByUserId() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId("u-1003");
        userProfile.setEmail("u1003@example.com");
        userProfile.setName("Cindy");
        userProfileRepository.save(userProfile);
        Optional<UserProfile> byUserId = userProfileRepository.findByUserId("u-1003");
        System.out.println("Found by userId: " + byUserId);
    }

    @Test
    void update() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId("u-1004");
        userProfile.setEmail("old@example.com");
        userProfile.setName("David");
        UserProfile saved = userProfileRepository.save(userProfile);

        saved.setEmail("new@example.com");
        saved.setName("David-New");
        UserProfile updated = userProfileRepository.save(saved);
        System.out.println("Updated user profile: " + updated);
    }

    @Test
    void deleteById() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId("u-1005");
        userProfile.setEmail("del@example.com");
        userProfile.setName("Ellen");
        UserProfile saved = userProfileRepository.save(userProfile);
        UUID id = saved.getId();

        userProfileRepository.deleteById(id);
        Optional<UserProfile> after = userProfileRepository.findById(id);
        System.out.println("Deleted id: " + id + ", find after delete: " + after);
    }
}
