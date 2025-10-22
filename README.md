# Travel Agent Project

Microservices monorepo (Spring Boot 3, Java 17) with Kafka and an AI assistant (Ollama + LangChain4j tools) for flight search and bookings.

What you get now:
- Multi-module Maven build: common, booking-service, payment-service, profile-service, assistant-service
- Ready-to-run Docker Compose stack (ZooKeeper, Kafka, services)
- Assistant with tool-calling (LangChain4j 0.36) and robust flows: flight search, advice, selection, booking, cancellation, and rescheduling
- Deterministic local flights dataset (CSV + synthetic capitals) and conversation memory


## Requirements
- Java 17+
- Maven 3.9+
- Docker + Docker Compose
- Ollama running on your machine with the model pulled: llama3-groq-tool-use:8b


## Build
- Full build (skip tests):
  - PowerShell: mvn -q -DskipTests package
- One module with deps (example):
  - mvn -q -pl assistant-service -am -DskipTests package


## Quick start (Docker Compose)
1) Build all JARs:
   - mvn -q -DskipTests package
2) Start the stack:
   - docker compose up --build -d
3) Open the assistant chat:
   - http://localhost:18090/chat.html
4) Services and ports:
   - booking-service: http://localhost:18081 → 8081
   - payment-service: http://localhost:18082 → 8082
   - profile-service: http://localhost:18083 → 8083
   - assistant-service: http://localhost:18090 → 8090

Notes
- In Compose, assistant connects to host Ollama via http://host.docker.internal:11434.
- Compose sets OLLAMA_NUM_GPU=0 and OLLAMA_NUM_CTX=2048 so the model runs on CPU with a smaller context (helps low-end laptops) and disables fallback by default.


## Kafka
- Topics:
  - travel.bookings
  - travel.payments
- Bootstrap server in Compose: kafka:9092 (services do not use localhost:9092 inside containers).


## HTTP APIs
### booking-service
- POST /api/bookings → create (202 Accepted). Publishes BookingCreatedEvent and returns:
  - { status: "PUBLISHED", topic: "travel.bookings", key: "<userId>:<tripId>", bookingId: "<UUID>" }
- GET /api/bookings → list
- GET /api/bookings/{id} → get (400 on invalid UUID, 404 if not found)
- PUT /api/bookings/{id} → update
- DELETE /api/bookings/{id} → delete (204 on success; 400/404 on errors)

### assistant-service
Two endpoints are supported:
- POST /api/assistant/agent/ask
  - Body: { "prompt": "...", "sessionId": "optional" }
  - Uses the Agent (LLM + tools + chat memory)
- POST /api/assistant/query
  - Body: { "prompt": "...", "mode": "agent|llm", "sessionId": "optional", "userId": "optional" }
  - agent mode (default): uses tools and server memory
  - llm mode: plain model prompt/response (no tools)


## Assistant: behavior and tools
- Model & runtime
  - LangChain4j 0.36 with Ollama
  - Single model by design: llama3-groq-tool-use:8b
  - CPU-first defaults: num_gpu=0, num_ctx=2048 (configurable); no automatic model fallback by default
- Memory
  - Chat memory: per session (window size 50). Tools write short TOP‑5 summaries into chat so the model "sees" results.
  - Server memory per session: last search list, last chosen flight, last booking id, pending cancel list, reschedule state.
- Agent policy
  - Slot-filling first (ask only for missing origin/destination/date), consults chat context, calls tools when needed.
  - For high-level advice, answers briefly, then offers a search and asks for missing slots.

Tools and contracts (structured JSON)
- FlightSearchTool
  - searchFlights(origin, destination, date) → { status, data: [flights], error? }
  - cheapestFlight(origin, destination, date) → { status, data: {flight}, error? }
  - suggestDestinations(origin, date?, limit?) → { status, data: [one per destination], error? }
  - recommendFromOrigin(origin, date?) → { status, data: {flight}, error? }
  - Dataset-backed; writes a TOP‑5 summary to chat memory and stores last results server-side
- SelectFromLastSearchTool
  - selectFromLast(memoryId, ordinal?, cheapest?, earliest?, latest?, date?, destination?, maxPrice?, carrier?, timeRange?, nonstop?)
  - Returns { status, data: { selected, ordinal, tripId }, error? }
- BookingTools (calls booking-service; resilient)
  - createBooking(userId, tripId, price) → { status, httpStatus, data|error }
    - Adds Idempotency-Key: userId:tripId
  - listBookings() → JSON array
  - getBooking(id) → JSON booking or {}
  - updateBooking(id, userId, tripId, price) → { status, httpStatus, data|error }
  - deleteBooking(id) → { status, httpStatus, message|error }
  - All calls include timeouts, limited retries, and a simple circuit breaker

User flows supported
- Flight search and advice (including "cheapest" and open-destination suggestions)
- Selection by ordinal/filters ("first", "2nd", cheapest, evening, maxPrice, carrier, etc.) via SelectFromLast
- Booking creation
  - Requires userId; remembers last bookingId on success
- Booking cancellation
  - "cancel <UUID>" or "cancel first/last" after listing; if none specified, shows a few and accepts an ordinal reply
- Rescheduling
  - "reschedule booking <UUID> to YYYY-MM-DD" or "reschedule last"
  - Creates new booking first; validates ownership; then cancels old; rolls back if cancel fails

Ownership validation
- When userId is known in the session, cancellation/reschedule is allowed only for that user’s bookings.


## Local flights data
- CSV bundled with assistant-service: assistant-service/src/main/resources/data/flights.csv (~400 rows)
- On startup, +N synthetic capital-to-capital flights are generated (default 500) across world capitals and late‑Dec 2025 dates
- Deduplication by (carrier+flightNumber+date)
- Times stored as ISO_OFFSET_DATE_TIME in local timezone
- Airport/city normalization with EN/RU aliases (IATA↔city), so "NYC", "Нью-Йорк", "San Francisco" all work
- Fast trip lookup by tripId (<carrier>-<flightNumber>-<date>) via a prebuilt index

Config (assistant-service application.yml / env overrides)
- assistant.ollama.base-url (OLLAMA_BASE_URL, default http://localhost:11434)
- assistant.ollama.model (OLLAMA_MODEL, default llama3-groq-tool-use:8b)
- assistant.ollama.request-timeout-ms (OLLAMA_TIMEOUT_MS)
- assistant.ollama.temperature (OLLAMA_TEMPERATURE)
- assistant.ollama.num-gpu (OLLAMA_NUM_GPU, default 0)
- assistant.ollama.num-ctx (OLLAMA_NUM_CTX, default 2048)
- assistant.ollama.no-fallback (OLLAMA_NO_FALLBACK, default true)
- assistant.agent.tools-enabled (ASSISTANT_AGENT_TOOLS_ENABLED, default true)
- assistant.server-nlu.enabled (ASSISTANT_SERVER_NLU_ENABLED, default false)
- assistant.tools.booking.base-url (BOOKING_BASE_URL)
- assistant.tools.booking.timeout-ms (BOOKING_TIMEOUT_MS, default 5000)
- assistant.tools.booking.retries (BOOKING_RETRIES, default 2)
- assistant.tools.flight.dataset (ASSISTANT_TOOLS_FLIGHT_DATASET)
- assistant.tools.flight.synthetic-count (ASSISTANT_TOOLS_FLIGHT_SYNTHETIC_COUNT, default 500)


## Interactive chat (web)
- Open http://localhost:18090/chat.html
- The page calls POST /api/assistant/query and shows replies.

Tips to try
- "Find the cheapest flight from SFO to JFK on 2025-12-24"
- "From London recommend a destination on 2025-12-24"
- After a list: "first" or "select the cheapest"
- "My user id is u-100" then "book it"
- "show my bookings"
- "cancel last" or "cancel first one"
- "reschedule booking <UUID> to 2025-12-27" → pick a flight → "reschedule it"


## Troubleshooting
- CUDA_Host / out-of-memory when calling Ollama
  - Ensure the model is pulled: ollama pull llama3-groq-tool-use:8b
  - Run on CPU: set OLLAMA_NUM_GPU=0 (Compose already does). Lower OLLAMA_NUM_CTX if needed.
- Some models don’t support /api/generate
  - The assistant automatically falls back to /api/chat in plain LLM mode. Agent tool-calling uses the configured chat model.
- Connectivity from containers to host Ollama
  - Use http://host.docker.internal:11434 in Docker (already set in Compose), http://localhost:11434 locally.
- Windows Docker engine issues
  - Run scripts\windows\docker-repair.ps1 or restart Docker Desktop.


## Demo data (seeders)
- booking-service and profile-service seed initial data if DBs are empty (H2 file DBs under /data via volumes)
- Verify:
  - Profiles: GET http://localhost:18083/api/profiles
  - Bookings: GET http://localhost:18081/api/bookings
- Reset: docker compose down; remove volumes; docker compose up --build -d


## Tests
- mvn -q test (repo-wide)
- mvn -q -pl payment-service -am test (module)


## Architecture and ADR
- docs/adr/* contain context and decisions for bounded contexts and services

## License
MIT
