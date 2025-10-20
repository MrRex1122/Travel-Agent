package com.example.travel.assistant.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Tools for looking up user profiles by calling profile-service.
 */
@Component
public class ProfileLookupTool {

    private final WebClient webClient;

    public ProfileLookupTool(@Value("${assistant.tools.profile.base-url:http://localhost:18083}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Tool("List user profiles. Returns a JSON array as text.")
    public String listProfiles() {
        try {
            var resp = webClient.get()
                    .uri("/api/profiles")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return resp != null ? resp : "[]";
        } catch (Exception ex) {
            return "Failed to list profiles: " + ex.getMessage();
        }
    }

    @Tool("Get user profile by its UUID id. Returns profile JSON as text or a not-found message.")
    public String getProfileById(String profileId) {
        try {
            var resp = webClient.get()
                    .uri("/api/profiles/{id}", profileId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return resp != null ? resp : "{}";
        } catch (Exception ex) {
            return "Failed to get profile: " + ex.getMessage();
        }
    }
}
