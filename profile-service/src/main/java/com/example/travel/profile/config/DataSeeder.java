package com.example.travel.profile.config;

import com.example.travel.profile.domain.UserProfile;
import com.example.travel.profile.repository.UserProfileRepository;
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
    CommandLineRunner seedProfiles(UserProfileRepository repo) {
        return args -> seed(repo);
    }

    @Transactional
    void seed(UserProfileRepository repo) {
        long count = repo.count();
        if (count > 0) {
            log.info("[profile-service] Skipping seed: {} profiles already present", count);
            return;
        }
        List<UserProfile> demo = List.of(
                mk("u-100", "Alice Johnson", "alice@example.com", "+1-202-555-0100", "GOLD", "{\"seat\":\"aisle\",\"bags\":1}"),
                mk("u-101", "Bob Smith", "bob@example.com", "+1-202-555-0101", "SILVER", "{\"seat\":\"window\",\"meal\":\"vegan\"}"),
                mk("u-102", "Carol Lee", "carol@example.com", "+1-202-555-0102", "PLATINUM", "{\"notify\":true}"),
                mk("u-103", "Diego Martinez", "diego@example.com", "+34-91-555-0103", "BRONZE", "{\"lang\":\"es\"}"),
                mk("u-104", "Eva Müller", "eva@example.de", "+49-30-555-0104", "GOLD", "{\"lang\":\"de\",\"seat\":\"any\"}")
        );
        repo.saveAll(demo);
        log.info("[profile-service] Seeded {} demo profiles", demo.size());
    }

    private static UserProfile mk(String userId, String name, String email, String phone, String tier, String prefsJson) {
        UserProfile p = new UserProfile();
        p.setUserId(userId);
        p.setName(name);
        p.setEmail(email);
        p.setPhone(phone);
        p.setLoyaltyTier(tier);
        p.setPreferencesJson(prefsJson);
        return p;
    }
}
