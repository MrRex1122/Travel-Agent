package com.example.travel.profile.web;

import com.example.travel.profile.domain.UserProfile;
import com.example.travel.profile.dto.ProfileRequest;
import com.example.travel.profile.repository.UserProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final UserProfileRepository repository;

    public ProfileController(UserProfileRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<UserProfile> list() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProfileRequest req) {
        if (req.getUserId() == null || req.getUserId().isBlank()) {
            return ResponseEntity.badRequest().body("userId is required");
        }
        if (repository.existsByUserId(req.getUserId())) {
            return ResponseEntity.badRequest().body("userId already exists");
        }
        UserProfile p = map(req, new UserProfile());
        p = repository.save(p);
        return ResponseEntity.created(URI.create("/api/profiles/" + p.getId())).body(p);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody ProfileRequest req) {
        return repository.findById(id).map(exist -> {
            if (req.getName() != null) exist.setName(req.getName());
            if (req.getEmail() != null) exist.setEmail(req.getEmail());
            if (req.getPhone() != null) exist.setPhone(req.getPhone());
            if (req.getLoyaltyTier() != null) exist.setLoyaltyTier(req.getLoyaltyTier());
            if (req.getPreferencesJson() != null) exist.setPreferencesJson(req.getPreferencesJson());
            return ResponseEntity.ok(repository.save(exist));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private UserProfile map(ProfileRequest req, UserProfile p) {
        p.setUserId(req.getUserId());
        p.setName(req.getName());
        p.setEmail(req.getEmail());
        p.setPhone(req.getPhone());
        p.setLoyaltyTier(req.getLoyaltyTier());
        p.setPreferencesJson(req.getPreferencesJson());
        return p;
    }
}
