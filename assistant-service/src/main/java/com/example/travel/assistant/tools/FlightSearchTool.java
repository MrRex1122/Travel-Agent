package com.example.travel.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import com.example.travel.assistant.service.AgentService;

import com.example.travel.assistant.memory.SharedChatMemoryProvider;
import com.example.travel.assistant.memory.ConversationContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
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
    private final SharedChatMemoryProvider memoryProvider;
    private final ObjectProvider<AgentService> agentServiceProvider;
    private final Map<String, Map<String, Object>> tripIndex = new HashMap<>();
    private final int syntheticCount;
    private final ZoneId systemZone = ZoneId.systemDefault();

    // --- Standard response wrappers for tools ---
    private String wrapOk(Object data) {
        try {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("status", "OK");
            out.put("data", data);
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            return "{\"status\":\"OK\"}";
        }
    }

    private String wrapError(String code, String message) {
        try {
            java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
            if (code != null) err.put("code", code);
            if (message != null) err.put("message", message);
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("status", "ERROR");
            out.put("error", err);
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            return "{\"status\":\"ERROR\"}";
        }
    }

    // Simple normalization dictionaries: alias (EN/RU/common forms) -> IATA; and IATA -> canonical city
    private static final Map<String, String> ALIAS_TO_IATA = new HashMap<>();
    private static final Map<String, String> IATA_TO_CITY = new HashMap<>();
    static {
        // US
        addMapping("SFO", "San Francisco", "Сан-Франциско", "SF");
        addMapping("JFK", "New York", "Нью-Йорк", "NYC");
        addMapping("LAX", "Los Angeles", "Лос-Анджелес", "LA");
        addMapping("IAD", "Washington", "Вашингтон", "Washington DC", "DC");
        // Europe
        addMapping("LHR", "London", "Лондон");
        addMapping("LGW", "London", "Лондон", "Gatwick");
        addMapping("CDG", "Paris", "Париж");
        addMapping("BER", "Berlin", "Берлин");
        addMapping("MAD", "Madrid", "Мадрид");
        addMapping("FCO", "Rome", "Рим");
        addMapping("DUB", "Dublin", "Дублин");
        addMapping("LIS", "Lisbon", "Лиссабон");
        addMapping("VIE", "Vienna", "Вена");
        addMapping("PRG", "Prague", "Прага");
        addMapping("WAW", "Warsaw", "Варшава");
        addMapping("AMS", "Amsterdam", "Амстердам");
        addMapping("ZRH", "Zurich", "Цюрих");
        addMapping("OSL", "Oslo", "Осло");
        addMapping("CPH", "Copenhagen", "Копенгаген");
        addMapping("HEL", "Helsinki", "Хельсинки");
        addMapping("ARN", "Stockholm", "Стокгольм");
        // Asia / MEA / Others
        addMapping("SVO", "Moscow", "Москва");
        addMapping("PEK", "Beijing", "Пекин", "Beijing City");
        addMapping("HND", "Tokyo", "Токио");
        addMapping("ICN", "Seoul", "Сеул");
        addMapping("BKK", "Bangkok", "Бангкок");
        addMapping("SIN", "Singapore", "Сингапур");
        addMapping("CGK", "Jakarta", "Джакарта");
        addMapping("DEL", "New Delhi", "Дели", "Delhi");
        addMapping("CBR", "Canberra", "Канберра");
        addMapping("WLG", "Wellington", "Веллингтон");
        addMapping("MNL", "Manila", "Манила");
        addMapping("HAN", "Hanoi", "Ханой");
        addMapping("RUH", "Riyadh", "Эр-Рияд");
        addMapping("AUH", "Abu Dhabi", "Абу-Даби");
        addMapping("DOH", "Doha", "Доха");
        addMapping("CAI", "Cairo", "Каир");
        addMapping("NBO", "Nairobi", "Найроби");
        addMapping("JNB", "Johannesburg", "Йоханнесбург");
        addMapping("ADD", "Addis Ababa", "Аддис-Абеба");
        addMapping("ATH", "Athens", "Афины");
        addMapping("TLV", "Tel Aviv", "Тель-Авив");
        addMapping("TUN", "Tunis", "Тунис");
        addMapping("ALG", "Algiers", "Алжир");
        addMapping("DKR", "Dakar", "Дакар");
    }

    private static void addMapping(String iata, String city, String... aliases) {
        IATA_TO_CITY.put(iata.toUpperCase(Locale.ROOT), city);
        // map base city name as alias too
        ALIAS_TO_IATA.put(slug(city), iata.toUpperCase(Locale.ROOT));
        ALIAS_TO_IATA.put(iata.toUpperCase(Locale.ROOT), iata.toUpperCase(Locale.ROOT));
        for (String a : aliases) {
            if (a == null) continue;
            ALIAS_TO_IATA.put(slug(a), iata.toUpperCase(Locale.ROOT));
        }
    }

    public FlightSearchTool(ResourceLoader resourceLoader,
                            @Value("${assistant.tools.flight.dataset:classpath:/data/flights.csv}") String datasetLocation,
                            SharedChatMemoryProvider memoryProvider,
                            ObjectProvider<AgentService> agentServiceProvider,
                            @Value("${assistant.tools.flight.synthetic-count:${ASSISTANT_TOOLS_FLIGHT_SYNTHETIC_COUNT:500}}") int syntheticCount) {
        this.syntheticCount = Math.max(0, syntheticCount);
        this.memoryProvider = memoryProvider;
        this.agentServiceProvider = agentServiceProvider;
        this.dataset = loadDataset(resourceLoader, datasetLocation);
        buildTripIndex();
    }

    @Tool("Search flights for given origin, destination and date (YYYY-MM-DD). origin/destination can be a city name or IATA code. Date can be written in natural language; you MUST normalize it to YYYY-MM-DD (infer year to nearest future date). If normalization fails, ask only for the date. Returns a structured JSON: { status, data: [flights], error? }. If any argument is missing, ask the user only for that specific piece.")
    public String searchFlights(String origin, String destination, String date) {
        try {
            var validation = validate(origin, destination, date);
            if (validation != null) return wrapError("VALIDATION", validation);

            List<Map<String, Object>> flights = fromDataset(origin, destination, date);
            if (flights.isEmpty()) {
                flights = mockFlights(origin, destination, date);
            }
            writeTopSummaryToChatMemory("last_search: " + origin + " -> " + destination + " on " + date, flights);
            rememberServerLastSearch(flights);
            return wrapOk(flights);
        } catch (Exception e) {
            return wrapError("INTERNAL_ERROR", "Failed to search flights: " + e.getMessage());
        }
    }

    @Tool("Find the cheapest flight for given origin, destination and date (YYYY-MM-DD). origin/destination can be a city name or IATA code. Date can be written in natural language; you MUST normalize it to YYYY-MM-DD (infer year to nearest future date). If normalization fails, ask only for the date. Returns a structured JSON: { status, data: {flight}, error? }. If any argument is missing, ask the user only for that specific piece.")
    public String cheapestFlight(String origin, String destination, String date) {
        try {
            var validation = validate(origin, destination, date);
            if (validation != null) return wrapError("VALIDATION", validation);

            List<Map<String, Object>> flights = fromDataset(origin, destination, date);
            if (flights.isEmpty()) {
                flights = mockFlights(origin, destination, date);
            }
            writeTopSummaryToChatMemory("last_search (cheapest candidates): " + origin + " -> " + destination + " on " + date, flights);
            Map<String, Object> best = flights.stream()
                    .min(Comparator.comparingDouble(f -> ((Number) f.get("price")).doubleValue()))
                    .orElse(new java.util.LinkedHashMap<>());
            rememberServerLastSearch(flights);
            if (best.get("price") != null) {
                rememberChosenServer(best);
            }
            if (best.isEmpty()) {
                return wrapError("NOT_FOUND", "No flights found for the given route and date");
            }
            return wrapOk(best);
        } catch (Exception e) {
            return wrapError("INTERNAL_ERROR", "Failed to find cheapest flight: " + e.getMessage());
        }
    }

    @Tool("Suggest destinations from a given origin. Date is optional (YYYY-MM-DD). If provided in natural language, normalize it to YYYY-MM-DD (infer year to nearest future date). If normalization fails, ask only for the date. If date is empty, pick the cheapest per destination across all dates in the dataset. Returns a structured JSON: { status, data: [flights(one per destination)] }.")
    public String suggestDestinations(String origin, String date, Integer limit) {
        try {
            if (origin == null || origin.isBlank()) return wrapError("VALIDATION", "origin is required");
            int topN = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);
            List<Map<String, Object>> options = fromDatasetAnyDestination(origin, date)
                    .stream()
                    .collect(Collectors.groupingBy(r -> String.valueOf(r.get("destination"))))
                    .values().stream()
                    .map(list -> list.stream().min(Comparator.comparingDouble(r -> ((Number) r.get("price")).doubleValue())).orElse(null))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingDouble(r -> ((Number) r.get("price")).doubleValue()))
                    .limit(topN)
                    .collect(Collectors.toList());
            writeTopSummaryToChatMemory("last_search: from " + origin + (date != null && !date.isBlank() ? (" on " + date) : ""), options);
            rememberServerLastSearch(options);
            return wrapOk(options);
        } catch (Exception e) {
            return wrapError("INTERNAL_ERROR", "Failed to suggest destinations: " + e.getMessage());
        }
    }

    @Tool("Recommend the best (cheapest) flight from a given origin. Date is optional (YYYY-MM-DD). If provided in natural language, normalize it to YYYY-MM-DD (infer year to nearest future date). If normalization fails, ask only for the date. If date is empty, search across all dates in the dataset. Returns a structured JSON: { status, data: {flight}, error? }.")
    public String recommendFromOrigin(String origin, String date) {
        try {
            if (origin == null || origin.isBlank()) return wrapError("VALIDATION", "origin is required");
            List<Map<String, Object>> options = fromDatasetAnyDestination(origin, date);
            if (options.isEmpty()) return wrapError("NOT_FOUND", "No flights found");
            Map<String, Object> best = options.stream()
                    .min(Comparator.comparingDouble(r -> ((Number) r.get("price")).doubleValue()))
                    .orElse(options.get(0));
            writeTopSummaryToChatMemory("last_search: from " + origin + (date != null && !date.isBlank() ? (" on " + date) : ""), options);
            rememberServerLastSearch(options);
            rememberChosenServer(best);
            return wrapOk(best);
        } catch (Exception e) {
            return wrapError("INTERNAL_ERROR", "Failed to recommend flight: " + e.getMessage());
        }
    }

    private String validate(String origin, String destination, String date) {
        if (origin == null || origin.isBlank()) return "origin is required";
        if (destination == null || destination.isBlank()) return "destination is required";
        if (date == null || date.isBlank()) return "date is required";
        if (origin.equalsIgnoreCase(destination)) return "origin and destination must differ";
        // very light YYYY-MM-DD check
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return "date must be in format YYYY-MM-DD";
        // date should not be in the past relative to system timezone
        try {
            LocalDate d = LocalDate.parse(date);
            LocalDate today = LocalDate.now(systemZone);
            if (d.isBefore(today)) {
                return "date is in the past; please provide a future date (YYYY-MM-DD)";
            }
        } catch (Exception ignore) {}
        return null;
    }

    private void rememberServerLastSearch(java.util.List<java.util.Map<String, Object>> list) {
        try {
            String memId = ConversationContext.getMemoryId();
            if (memId == null || list == null || list.isEmpty()) return;
            AgentService svc = agentServiceProvider.getIfAvailable();
            if (svc != null) {
                svc.rememberLastSearch(memId, list);
            }
        } catch (Exception ignore) {}
    }

    private void rememberChosenServer(java.util.Map<String, Object> obj) {
        try {
            String memId = ConversationContext.getMemoryId();
            if (memId == null || obj == null || obj.isEmpty()) return;
            AgentService svc = agentServiceProvider.getIfAvailable();
            if (svc != null) {
                svc.rememberChosen(memId, obj);
            }
        } catch (Exception ignore) {}
    }

    private void writeTopSummaryToChatMemory(String context, List<Map<String, Object>> list) {
        try {
            String memId = ConversationContext.getMemoryId();
            if (memId == null || list == null || list.isEmpty()) return;
            var memory = memoryProvider.get(memId);
            if (memory == null) return;
            StringBuilder sb = new StringBuilder();
            if (context != null && !context.isBlank()) {
                sb.append("Context: ").append(context).append("\n");
            }
            int n = Math.min(list.size(), 5);
            for (int i = 0; i < n; i++) {
                Map<String, Object> f = list.get(i);
                sb.append(i + 1).append(") ")
                  .append(String.valueOf(f.getOrDefault("carrier", "?"))).append(' ')
                  .append(String.valueOf(f.getOrDefault("flightNumber", "?")))
                  .append(" on ").append(String.valueOf(f.getOrDefault("date", "?")))
                  .append("  ")
                  .append(String.valueOf(f.getOrDefault("origin", "?"))).append(" -> ")
                  .append(String.valueOf(f.getOrDefault("destination", "?")))
                  .append(", price=")
                  .append(String.valueOf(f.getOrDefault("price", "?"))).append(' ')
                  .append(String.valueOf(f.getOrDefault("currency", "")))
                  .append("\n");
            }
            if (list.size() > n) sb.append("(+").append(list.size() - n).append(" more)\n");
            memory.add(AiMessage.from(sb.toString().trim()));
        } catch (Exception ignore) {
        }
    }

    private List<Map<String, Object>> fromDataset(String origin, String destination, String date) {
        if (dataset == null || dataset.isEmpty()) return Collections.emptyList();
        String o = origin.trim();
        String d = destination.trim();
        String dt = date.trim();
        return dataset.stream()
                .filter(r -> dt.equals(r.get("date")))
                .filter(r -> matchesPlace(o, String.valueOf(r.get("origin")), String.valueOf(r.get("originCity")))
                          && matchesPlace(d, String.valueOf(r.get("destination")), String.valueOf(r.get("destinationCity"))))
                .sorted(Comparator.comparingDouble(r -> ((Number) r.get("price")).doubleValue()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> fromDatasetAnyDestination(String origin, String dateOrNull) {
        if (dataset == null || dataset.isEmpty()) return Collections.emptyList();
        String o = origin.trim();
        return dataset.stream()
                .filter(r -> dateOrNull == null || dateOrNull.isBlank() || String.valueOf(r.get("date")).startsWith(dateOrNull.trim()))
                .filter(r -> matchesPlace(o, String.valueOf(r.get("origin")), String.valueOf(r.get("originCity"))))
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
            LocalDateTime dep = LocalDate.parse(date).atTime(6 + i * 3, (i * 13) % 60);
            LocalDateTime arr = dep.plusHours(3).plusMinutes(45);
            f.put("departure", toIsoOffset(dep));
            f.put("arrival", toIsoOffset(arr));
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
                    r.put("departure", toIsoOffset(parts[4], parts[5]));
                    r.put("arrival", toIsoOffset(parts[4], parts[6]));
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
                int before = rows.size();
                // Augment with additional synthetic capital-to-capital flights for wider coverage (configurable)
                if (syntheticCount > 0) {
                    rows.addAll(generateCapitalFlights(syntheticCount));
                }
                // Deduplicate by (carrier+flightNumber+date)
                java.util.LinkedHashMap<String, Map<String, Object>> unique = new java.util.LinkedHashMap<>();
                for (Map<String, Object> r : rows) {
                    unique.putIfAbsent(uniqueKey(r), r);
                }
                java.util.List<Map<String, Object>> out = new java.util.ArrayList<>(unique.values());
                log.info("[FlightSearchTool] Loaded {} flights from dataset {} and augmented +{} capital flights (unique total={}).", before, location, syntheticCount, out.size());
                return out;
            }
        } catch (Exception e) {
            log.warn("[FlightSearchTool] Failed to load dataset {}: {}. Using mock generator.", location, e.toString());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> generateCapitalFlights(int count) {
        List<Map<String, Object>> list = new ArrayList<>(count);
        String[][] capitals = new String[][]{
                {"LHR","London"}, {"CDG","Paris"}, {"BER","Berlin"}, {"MAD","Madrid"}, {"FCO","Rome"},
                {"IAD","Washington"}, {"MEX","Mexico City"}, {"BSB","Brasilia"}, {"EZE","Buenos Aires"},
                {"SVO","Moscow"}, {"PEK","Beijing"}, {"HND","Tokyo"}, {"ICN","Seoul"}, {"BKK","Bangkok"},
                {"SIN","Singapore"}, {"CGK","Jakarta"}, {"DEL","New Delhi"}, {"CBR","Canberra"}, {"WLG","Wellington"},
                {"MNL","Manila"}, {"HAN","Hanoi"}, {"RUH","Riyadh"}, {"AUH","Abu Dhabi"}, {"DOH","Doha"},
                {"CAI","Cairo"}, {"NBO","Nairobi"}, {"JNB","Johannesburg"}, {"ADD","Addis Ababa"}, {"ATH","Athens"},
                {"OSL","Oslo"}, {"CPH","Copenhagen"}, {"ARN","Stockholm"}, {"HEL","Helsinki"}, {"DUB","Dublin"},
                {"LIS","Lisbon"}, {"VIE","Vienna"}, {"PRG","Prague"}, {"ZAG","Zagreb"}, {"BUD","Budapest"},
                {"BTS","Bratislava"}, {"WAW","Warsaw"}, {"BRU","Brussels"}, {"AMS","Amsterdam"}, {"ZRH","Zurich"},
                {"IST","Istanbul"}, {"TLV","Tel Aviv"}, {"TUN","Tunis"}, {"ALG","Algiers"}, {"DKR","Dakar"}
        };
        String[] carriers = {"CapitalAir", "MetroFly", "EuroWings", "GlobeAir"};
        Random rnd = new Random(424242); // deterministic
        String[] dates = new String[]{"2025-12-20","2025-12-21","2025-12-22","2025-12-23","2025-12-24","2025-12-25","2025-12-26","2025-12-27","2025-12-28","2025-12-29"};
        for (int i = 0; i < count; i++) {
            int oi = rnd.nextInt(capitals.length);
            int di = rnd.nextInt(capitals.length);
            if (di == oi) { di = (di + 1) % capitals.length; }
            String oCode = capitals[oi][0];
            String oCity = capitals[oi][1];
            String dCode = capitals[di][0];
            String dCity = capitals[di][1];
            String date = dates[rnd.nextInt(dates.length)];
            int depH = 5 + rnd.nextInt(18); // 05..22
            int depM = (rnd.nextInt(4)) * 15; // 00,15,30,45
            int durH = 2 + rnd.nextInt(9); // 2..10 hours
            int arrH = (depH + durH) % 24;
            int arrM = (depM + 30) % 60;
            double base = 120 + rnd.nextInt(600); // 120..719
            double total = Math.round((base) * 100.0) / 100.0;
            Map<String, Object> r = new LinkedHashMap<>();
            String carrier = carriers[i % carriers.length];
            r.put("carrier", carrier);
            r.put("flightNumber", carrier.substring(0, 2).toUpperCase(Locale.ROOT) + (1000 + i));
            r.put("origin", oCode);
            r.put("destination", dCode);
            r.put("date", date);
            LocalDate dateObj = LocalDate.parse(date);
            LocalDateTime dep = dateObj.atTime(depH, depM);
            LocalDateTime arr = dep.plusHours(durH).plusMinutes(30);
            r.put("departure", toIsoOffset(dep));
            r.put("arrival", toIsoOffset(arr));
            r.put("price", total);
            r.put("currency", "USD");
            r.put("originCity", oCity);
            r.put("destinationCity", dCity);
            list.add(r);
        }
        return list;
    }

    // Lookup a specific flight in the dataset by tripId (<carrier>-<flightNumber>-<date>),
    // where carrier in tripId may have spaces removed.
    public Map<String, Object> lookupFlightByTripId(String tripId) {
        if (tripId == null || tripId.isBlank()) return Collections.emptyMap();
        try {
            String t = tripId.trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.*)-(\\d{4}-\\d{2}-\\d{2})$").matcher(t);
            if (!m.find()) return Collections.emptyMap();
            String left = m.group(1);
            String date = m.group(2);
            int lastDash = left.lastIndexOf('-');
            if (lastDash <= 0) return Collections.emptyMap();
            String carrierPart = left.substring(0, lastDash);
            String flightNumber = left.substring(lastDash + 1);
            String key = normalizeTripKey(carrierPart, flightNumber, date);
            Map<String, Object> r = tripIndex.get(key);
            return r != null ? r : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static String normalizeCarrier(String s) {
        if (s == null) return "";
        return s.replace(" ", "").trim();
    }

    // ---- Helpers for normalization, ISO formatting, and indexing ----
    private static String slug(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
            else if (Character.isWhitespace(c) || c == '-') sb.append(' ');
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String canonicalIataOrNull(String input) {
        if (input == null || input.isBlank()) return null;
        String in = input.trim();
        if (in.matches("(?i)^[A-Z]{3}$")) return in.toUpperCase(Locale.ROOT);
        String byAlias = ALIAS_TO_IATA.get(slug(in));
        if (byAlias != null) return byAlias;
        return null;
    }

    private boolean matchesPlace(String input, String rowCode, String rowCity) {
        if (input == null || input.isBlank()) return false;
        String codeFromInput = canonicalIataOrNull(input);
        if (codeFromInput != null) {
            return codeFromInput.equalsIgnoreCase(String.valueOf(rowCode));
        }
        // Compare by city names (slug to ignore case/diacritics and punctuation)
        String inSlug = slug(input);
        String rowCitySlug = slug(String.valueOf(rowCity));
        if (!inSlug.isEmpty() && inSlug.equals(rowCitySlug)) return true;
        // Fallback: raw case-insensitive compare
        return input.equalsIgnoreCase(String.valueOf(rowCode)) || input.equalsIgnoreCase(String.valueOf(rowCity));
    }

    private String toIsoOffset(String date, String time) {
        try {
            LocalDate d = LocalDate.parse(date);
            LocalTime t;
            if (time != null && !time.isBlank()) {
                // ensure seconds present
                t = LocalTime.parse(time.length() == 5 ? time + ":00" : time);
            } else {
                t = LocalTime.MIDNIGHT;
            }
            return toIsoOffset(LocalDateTime.of(d, t));
        } catch (Exception e) {
            // If parse fails, return original concatenation as a last resort
            String ts = date + "T" + (time == null ? "00:00:00" : (time.length() == 5 ? time + ":00" : time));
            return ts + OffsetDateTime.now(systemZone).getOffset().toString();
        }
    }

    private String toIsoOffset(LocalDateTime ldt) {
        try {
            ZonedDateTime zdt = ldt.atZone(systemZone);
            return zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return ldt.toString();
        }
    }

    private String normalizeTripKey(String carrier, String flightNumber, String date) {
        return (normalizeCarrier(carrier) + "-" + String.valueOf(flightNumber) + "-" + String.valueOf(date))
                .toLowerCase(Locale.ROOT);
    }

    private String uniqueKey(Map<String, Object> r) {
        return (normalizeCarrier(String.valueOf(r.get("carrier"))) + "|"
                + String.valueOf(r.get("flightNumber")) + "|"
                + String.valueOf(r.get("date"))).toLowerCase(Locale.ROOT);
    }

    private void buildTripIndex() {
        tripIndex.clear();
        if (dataset == null) return;
        for (Map<String, Object> r : dataset) {
            String key = normalizeTripKey(String.valueOf(r.get("carrier")), String.valueOf(r.get("flightNumber")), String.valueOf(r.get("date")));
            tripIndex.putIfAbsent(key, r);
        }
    }
}
