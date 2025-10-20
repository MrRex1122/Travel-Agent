package com.example.travel.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Stub tool for flight search. Does not call any external system.
 * Returns deterministic mock data based on the input parameters so that
 * the agent can demonstrate flight search behavior.
 */
@Component
public class FlightSearchTool {

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool("Search flights for given origin, destination and date (YYYY-MM-DD). Returns JSON array as text.")
    public String searchFlights(String origin, String destination, String date) {
        try {
            var validation = validate(origin, destination, date);
            if (validation != null) return validation;

            List<Map<String, Object>> flights = mockFlights(origin, destination, date);
            return mapper.writeValueAsString(flights);
        } catch (Exception e) {
            return "Failed to search flights: " + e.getMessage();
        }
    }

    @Tool("Find the cheapest flight for given origin, destination and date (YYYY-MM-DD). Returns JSON object as text.")
    public String cheapestFlight(String origin, String destination, String date) {
        try {
            var validation = validate(origin, destination, date);
            if (validation != null) return validation;

            return mapper.writeValueAsString(
                    mockFlights(origin, destination, date)
                            .stream()
                            .min(Comparator.comparingDouble(f -> ((Number) f.get("price")).doubleValue()))
                            .orElse(Map.of("message", "No flights found"))
            );
        } catch (Exception e) {
            return "Failed to find cheapest flight: " + e.getMessage();
        }
    }

    private String validate(String origin, String destination, String date) {
        if (origin == null || origin.isBlank()) return "origin is required";
        if (destination == null || destination.isBlank()) return "destination is required";
        if (date == null || date.isBlank()) return "date is required";
        if (origin.equalsIgnoreCase(destination)) return "origin and destination must differ";
        // very light YYYY-MM-DD check
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return "date must be in format YYYY-MM-DD";
        return null;
    }

    private List<Map<String, Object>> mockFlights(String origin, String destination, String date) {
        // Create deterministic pseudo-random prices based on inputs, so the same
        // query yields identical results.
        int seed = Objects.hash(origin.toUpperCase(Locale.ROOT), destination.toUpperCase(Locale.ROOT), date);
        Random rnd = new Random(seed);

        List<Map<String, Object>> flights = new ArrayList<>();
        String[] carriers = {"ACME Air", "SkyLine", "BlueJet", "Nimbus"};
        String[] numbers = {"101", "202", "303", "404"};

        for (int i = 0; i < 4; i++) {
            double base = 80 + (rnd.nextInt(120)); // 80..199
            double taxes = Math.round((base * 0.21) * 100.0) / 100.0;
            double total = Math.round((base + taxes) * 100.0) / 100.0;
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("carrier", carriers[i]);
            f.put("flightNumber", carriers[i].substring(0, 2).toUpperCase(Locale.ROOT) + numbers[i]);
            f.put("origin", origin.toUpperCase(Locale.ROOT));
            f.put("destination", destination.toUpperCase(Locale.ROOT));
            f.put("date", date);
            f.put("departure", date + "T" + String.format("%02d:%02d", 6 + i * 3, (i * 13) % 60));
            f.put("arrival", date + "T" + String.format("%02d:%02d", 9 + i * 3, (i * 13 + 45) % 60));
            f.put("price", total);
            f.put("currency", "USD");
            flights.add(f);
        }
        return flights;
    }
}
