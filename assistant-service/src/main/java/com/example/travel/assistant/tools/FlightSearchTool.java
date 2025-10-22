package com.example.travel.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flight search tool that reads from a local dataset file (CSV) bundled with the app.
 * Fallbacks to deterministic mock data if the file is not available.
 */
@Component
public class FlightSearchTool {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchTool.class);


    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String, Object>> dataset; // loaded once and reused

    public FlightSearchTool(ResourceLoader resourceLoader,
                            @Value("${assistant.tools.flight.dataset:classpath:/data/flights.csv}") String datasetLocation) {
        this.dataset = loadDataset(resourceLoader, datasetLocation);
    }

    @Tool("Search flights for given origin, destination and date (YYYY-MM-DD). origin/destination can be a city name or IATA code. Returns JSON array as text. If any argument is missing, ask the user only for that specific piece.")
    public String searchFlights(String origin, String destination, String date) {
        try {
            var validation = validate(origin, destination, date);
            if (validation != null) return validation;

            List<Map<String, Object>> flights = fromDataset(origin, destination, date);
            if (flights.isEmpty()) {
                flights = mockFlights(origin, destination, date);
            }
            return mapper.writeValueAsString(flights);
        } catch (Exception e) {
            return "Failed to search flights: " + e.getMessage();
        }
    }

    @Tool("Find the cheapest flight for given origin, destination and date (YYYY-MM-DD). origin/destination can be a city name or IATA code. Returns JSON object as text. If any argument is missing, ask the user only for that specific piece.")
    public String cheapestFlight(String origin, String destination, String date) {
        try {
            var validation = validate(origin, destination, date);
            if (validation != null) return validation;

            List<Map<String, Object>> flights = fromDataset(origin, destination, date);
            if (flights.isEmpty()) {
                flights = mockFlights(origin, destination, date);
            }
            return mapper.writeValueAsString(
                    flights.stream()
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

    private List<Map<String, Object>> fromDataset(String origin, String destination, String date) {
        if (dataset == null || dataset.isEmpty()) return Collections.emptyList();
        String o = origin.trim();
        String d = destination.trim();
        String dt = date.trim();
        return dataset.stream()
                .filter(r -> dt.equals(r.get("date")))
                .filter(r -> {
                    String oCode = String.valueOf(r.get("origin"));
                    String dCode = String.valueOf(r.get("destination"));
                    String oCity = String.valueOf(r.get("originCity"));
                    String dCity = String.valueOf(r.get("destinationCity"));
                    boolean oMatch = oCode.equalsIgnoreCase(o) || oCity.equalsIgnoreCase(o);
                    boolean dMatch = dCode.equalsIgnoreCase(d) || dCity.equalsIgnoreCase(d);
                    return oMatch && dMatch;
                })
                .sorted(Comparator.comparingDouble(r -> ((Number) r.get("price")).doubleValue()))
                .collect(Collectors.toList());
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
            String o = origin.toUpperCase(Locale.ROOT);
            String d = destination.toUpperCase(Locale.ROOT);
            f.put("carrier", carriers[i]);
            f.put("flightNumber", carriers[i].substring(0, 2).toUpperCase(Locale.ROOT) + numbers[i]);
            f.put("origin", o);
            f.put("destination", d);
            f.put("date", date);
            f.put("departure", date + "T" + String.format("%02d:%02d", 6 + i * 3, (i * 13) % 60));
            f.put("arrival", date + "T" + String.format("%02d:%02d", 9 + i * 3, (i * 13 + 45) % 60));
            f.put("price", total);
            f.put("currency", "USD");
            flights.add(f);
        }
        return flights;
    }

    private List<Map<String, Object>> loadDataset(ResourceLoader loader, String location) {
        try {
            Resource resource = location.startsWith("classpath:")
                    ? new ClassPathResource(location.substring("classpath:".length()))
                    : loader.getResource(location);
            if (!resource.exists()) {
                log.warn("[FlightSearchTool] Dataset not found at {}. Using mock generator.", location);
                return Collections.emptyList();
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String header = br.readLine(); // read header
                if (header == null) return Collections.emptyList();
                List<Map<String, Object>> rows = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split(",");
                    if (parts.length < 9) continue;
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("carrier", parts[0]);
                    r.put("flightNumber", parts[1]);
                    r.put("origin", parts[2]);
                    r.put("destination", parts[3]);
                    r.put("date", parts[4]);
                    r.put("departure", parts[5]);
                    r.put("arrival", parts[6]);
                    try {
                        r.put("price", Double.parseDouble(parts[7]));
                    } catch (NumberFormatException nfe) {
                        r.put("price", 0.0);
                    }
                    r.put("currency", parts[8]);
                    // Require city columns to be present in the dataset; skip invalid lines
                    if (parts.length < 11) continue;
                    r.put("originCity", parts[9]);
                    r.put("destinationCity", parts[10]);
                    rows.add(r);
                }
                log.info("[FlightSearchTool] Loaded {} flights from dataset {}", rows.size(), location);
                return rows;
            }
        } catch (Exception e) {
            log.warn("[FlightSearchTool] Failed to load dataset {}: {}. Using mock generator.", location, e.toString());
            return Collections.emptyList();
        }
    }
}
