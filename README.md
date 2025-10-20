# Travel Agent Project

Monorepo template for microservices (Spring Boot 3, Java 17) with Kafka integration and an Assistant service.

Current version includes:
- Multi-module Maven parent POM with modules:
  - common — shared integration classes and constants (Event, Topics)
  - booking-service — REST API to create a booking and publish an event to Kafka
  - payment-service — consumer of booking events from Kafka
  - profile-service — skeleton of the profiles service
  - assistant-service — skeleton of the assistant (LLM) service
- Dockerfile for each service
- docker-compose for local stack: ZooKeeper, Kafka, and all services
- GitHub Actions (Maven CI)

## Requirements
- Java 17+ (Temurin/Oracle/OpenJDK)
- Maven 3.9+
- Docker and Docker Compose

## Build
- Full build (skip tests):
  - PowerShell: `mvn -q -DskipTests package`
- Build a single module (with dependencies):
  - Example: `mvn -q -pl booking-service -am -DskipTests package`

## Quick start (Docker Compose)
1) Build artifacts (JARs):
   - `mvn -q -DskipTests package`
2) Start the local stack:
   - `docker compose up --build`
3) Services and ports (host -> container):
   - booking-service: http://localhost:18081 (maps to 8081 in container)
   - payment-service: http://localhost:18082 (maps to 8082)
   - profile-service: http://localhost:18083 (maps to 8083)
   - assistant-service: http://localhost:18090 (maps to 8090)
   - Kafka broker: `kafka:9092` inside the docker-compose network; services do not use `localhost:9092` by default

> Note: docker-compose enables automatic Kafka topic creation (for local development).

## Kafka configuration
- Booking events topic: `travel.bookings` (see `common/src/main/java/.../Topics.java`)
- Payment outcomes topic: `travel.payments` (see `common/src/main/java/.../Topics.java`)
- Environment variables (docker-compose passes these to services):
  - `SPRING_KAFKA_BOOTSTRAP_SERVERS`: defaults to `kafka:9092` in containers; locally — `localhost:9092`
  - `SPRING_KAFKA_CONSUMER_GROUP` (for payment-service): defaults to `payment-service`
- Base serializers are configured in each service `application.yml`.

## API
### booking-service
- Create booking (publishes an event to Kafka):
  - POST `http://localhost:18081/api/bookings`
  - Body (JSON):
    ```json
    {
      "userId": "u-123",
      "tripId": "t-456",
      "price": 99.9
    }
    ```
  - 202 Accepted response example:
    ```json
    {
      "status": "PUBLISHED",
      "topic": "travel.bookings",
      "key": "u-123:t-456"
    }
    ```

### payment-service
- Consumes BookingCreatedEvent from `travel.bookings`, processes payment, and publishes `PaymentOutcomeEvent` to `travel.payments`.
  - Example booking-consumed log: `[payment-service] BookingCreated: userId=..., tripId=..., price=...`
  - Example outcome-published log: `[payment-service] Payment outcome published: AUTHORIZED/FAILED for bookingId=...`

### Health/Info (Actuator)
- booking-service: `GET http://localhost:18081/actuator/health`, `GET http://localhost:18081/actuator/info`
- payment-service: `GET http://localhost:18082/actuator/health`, `GET http://localhost:18082/actuator/info`
- profile-service/assistant-service: basic configuration, ports listed above (Actuator enabled in some services).

## Run without Docker (local)
- Option A (no Kafka needed): temporarily disable payment Kafka consumer and run services directly:
  - Set env var before run (PowerShell): `$env:APP_KAFKA_ENABLED="false"`
  - Start payment-service locally: `mvn -q -pl payment-service -am spring-boot:run`
  - Start booking-service locally in another terminal: `mvn -q -pl booking-service -am spring-boot:run`
- Option B (with Kafka): run Kafka separately (e.g., Confluent/Bitnami docker or local install) and set `spring.kafka.bootstrap-servers=localhost:9092`.
- Run a single service, example for booking-service:
  - `mvn -q -pl booking-service -am spring-boot:run`

## Repository structure
- `pom.xml` — parent POM (Spring Boot 3.3.3 dependency management)
- `common/` — shared DTOs and constants (Event, Topics)
- `booking-service/` — REST API and Kafka producer
- `payment-service/` — Kafka consumer
- `profile-service/` — skeleton
- `assistant-service/` — skeleton (Web/WebFlux, Actuator)
- `docker-compose.yml` — local stack (ZooKeeper, Kafka, services)
- `.github/workflows/build.yml` — CI (Maven build, JDK 21)

## Useful commands
- Rebuild and restart compose: `docker compose up --build -d`
- Tail service logs: `docker compose logs -f booking-service`
- Stop: `docker compose down`

## Troubleshooting: containers keep restarting
- Common cause: host port conflicts on Windows. We mapped host ports to high numbers to avoid this (18081/18082/18083/18090). If you still see restarts:
  - Check which process uses a port:
    - PowerShell: `Get-Process -Id (Get-NetTCPConnection -LocalPort 18081 -State Listen).OwningProcess`
  - Rebuild fresh images (ensures jars are copied):
    - `mvn -q -DskipTests package`
    - `docker compose build --no-cache`
    - `docker compose up -d`
  - Check logs:
    - `docker compose logs --no-color --tail=200 booking-service`
  - Verify health endpoints:
    - Booking: http://localhost:18081/actuator/health
    - Payment: http://localhost:18082/actuator/health

## Known limitations and next steps
- assistant-service: LLM client (Ollama) and tools — skeleton; API calls to be implemented.
- profile-service: no public API yet.
- Tests (unit/integration) are minimal — to be expanded.
- For production, prefer disabling auto-creation of topics and introducing schema governance.

## License
MIT (or adjust per project requirements).

## Architecture and ADR
- Context map: [docs/bounded-contexts.md](docs/bounded-contexts.md)
- ADR 0001 — Define bounded contexts and context map: [docs/adr/0001-bounded-contexts.md](docs/adr/0001-bounded-contexts.md)
- ADR 0002 — Booking context: [docs/adr/0002-booking-context.md](docs/adr/0002-booking-context.md)
- ADR 0003 — Payment context: [docs/adr/0003-payment-context.md](docs/adr/0003-payment-context.md)
- ADR 0004 — Profile context: [docs/adr/0004-profile-context.md](docs/adr/0004-profile-context.md)
- ADR 0005 — Assistant context: [docs/adr/0005-assistant-context.md](docs/adr/0005-assistant-context.md)

## OpenAPI specifications
- Booking Service: docs/openapi/booking-service.yaml
- Payment Service: docs/openapi/payment-service.yaml
- Profile Service: docs/openapi/profile-service.yaml
- Assistant Service: docs/openapi/assistant-service.yaml

How to use:
- Import the YAML into Swagger Editor (https://editor.swagger.io/) or Postman/Insomnia to view and generate clients.
- As new HTTP endpoints appear in services, the specifications will be extended. Currently the booking-service provides the domain endpoint (POST /api/bookings).


### assistant-service (Assistant & Agent)
The assistant offers three HTTP entry points:
- Raw LLM chat: simple prompt -> answer.
- Agent with tools: the LLM can call tools to interact with booking-service (create/list/get/update/delete bookings).
- Dialog endpoint: send a short chat history + choose mode (agent or llm) per request.

Prerequisites (LLM via Ollama):
- Install Ollama: https://ollama.com
- Pull a model (example): `ollama pull llama3.1`
- Start the server (only once; if it’s already running, don’t start a second one): `ollama serve` (listens on 11434)

Runtime notes:
- In Docker Compose, assistant connects to host Ollama via `http://host.docker.internal:11434` (already set in docker-compose.yml).
- Running assistant outside Docker defaults to `http://localhost:11434` (configurable via `assistant.ollama.base-url`).

Endpoints
1) Raw LLM chat
- POST `http://localhost:18090/api/assistant/ask`
- Request body:
```json
{ "prompt": "Say hello in one short sentence." }
```
- Response body:
```json
{ "answer": "Hello!" }
```

2) Agent mode (with tools)
- POST `http://localhost:18090/api/assistant/agent/ask`
- Example prompts and behavior:
  - "Create a booking for user u-123 on trip t-456 with price 99.9" → agent may call `createBooking` tool (HTTP call to booking-service) and return a summary.
  - "List my bookings" → agent may call `listBookings` and return the JSON or a summarized list.

3) Dialog with optional history and mode selection
- POST `http://localhost:18090/api/assistant/query`
- Request body example:
```json
{
  "prompt": "Show me my booking history.",
  "mode": "agent",
  "history": [
    { "role": "user", "content": "Hi" },
    { "role": "assistant", "content": "Hello! How can I help you?" }
  ]
}
```
- Notes:
  - `mode`: `agent` (default) uses tools; `llm` uses raw model.
  - `history` is not persisted server-side; it’s used only for this call.

Agent tools (available to the model)
- createBooking(userId, tripId, price): POST /api/bookings
- listBookings(): GET /api/bookings
- getBooking(bookingId): GET /api/bookings/{id}
- updateBooking(bookingId, userId, tripId, price): PUT /api/bookings/{id}
- deleteBooking(bookingId): DELETE /api/bookings/{id}

Configuration
- Assistant (application.yml / env overrides):
  - `assistant.ollama.base-url` (env: OLLAMA_BASE_URL)
  - `assistant.ollama.model` (env: OLLAMA_MODEL, default: llama3.1)
  - `assistant.ollama.request-timeout-ms` (env: OLLAMA_TIMEOUT_MS)
  - `assistant.ollama.temperature` (env: OLLAMA_TEMPERATURE)
  - `assistant.tools.booking.base-url` (env: BOOKING_BASE_URL)
- Defaults:
  - Outside Docker: BOOKING_BASE_URL=http://localhost:18081
  - In Docker: BOOKING_BASE_URL=http://booking-service:8081 (set in docker-compose.yml)

Persistence (service memory)
- Booking, Payment, and Profile services use file-based H2 DBs under `/data` with Docker volumes, so data persists across container restarts:
  - Volumes: `booking-data`, `payment-data`, `profile-data` (see docker-compose.yml)
- H2 consoles (use JDBC URLs below, username `sa`, empty password):
  - Booking:   http://localhost:18081/h2-console   JDBC: `jdbc:h2:file:/data/bookingdb;MODE=PostgreSQL;AUTO_SERVER=TRUE`
  - Payment:   http://localhost:18082/h2-console   JDBC: `jdbc:h2:file:/data/paymentdb;MODE=PostgreSQL;AUTO_SERVER=TRUE`
  - Profile:   http://localhost:18083/h2-console   JDBC: `jdbc:h2:file:/data/profiledb;MODE=PostgreSQL;AUTO_SERVER=TRUE`

Run assistant outside Docker (optional)
- Ensure Ollama is running on `http://localhost:11434`.
- From project root:
  - `mvn -q -pl assistant-service -am spring-boot:run`
- If you also run booking-service locally, start it too:
  - `mvn -q -pl booking-service -am spring-boot:run`
- Adjust envs as needed:
  - PowerShell (current session):
    - `$env:BOOKING_BASE_URL = "http://localhost:18081"`
    - `$env:OLLAMA_BASE_URL = "http://localhost:11434"`

Troubleshooting (assistant)
- "Connection refused" to Ollama:
  - Make sure the Ollama server is running and the base URL is correct for your runtime: `http://host.docker.internal:11434` in Docker, `http://localhost:11434` outside Docker.
- Long or timed-out responses:
  - Increase `assistant.ollama.request-timeout-ms`.
- Docker engine errors on Windows (pipe to dockerDesktopLinuxEngine):
  - Start Docker Desktop or run the helper script: `powershell -ExecutionPolicy Bypass -File scripts\windows\docker-repair.ps1`


## Windows troubleshooting: Ollama and Docker

### Quick auto-fix (Windows)
If Docker Desktop engine is not running and you see pipe errors (open //./pipe/dockerDesktopLinuxEngine), run:
- PowerShell: `powershell -ExecutionPolicy Bypass -File scripts\windows\docker-repair.ps1`
This will restart Docker Desktop and WSL integration, then wait until `docker info` succeeds.

Helpful scripts:
- Preflight checks: `powershell -ExecutionPolicy Bypass -File scripts\windows\dev-check.ps1`
- One‑shot build & up: `powershell -ExecutionPolicy Bypass -File scripts\windows\compose-up.ps1`

### Ollama: port 11434 is already in use
If you see:
- `Error: listen tcp 127.0.0.1:11434: bind: Only one usage of each socket address ... is normally permitted.`

It means an Ollama server is already running. Do not run a second `ollama serve`.

What to do:
- Check what uses the port:
  - PowerShell: `(Get-NetTCPConnection -LocalPort 11434 -State Listen).OwningProcess | ForEach-Object { Get-Process -Id $_ }`
- Stop the existing process (if needed):
  - Example: `Stop-Process -Id <PID> -Force`
- Or simply keep the existing Ollama server running and skip `ollama serve`. The assistant-service will connect to it.
- If you prefer a different port, start Ollama on another port and point assistant-service to it:
  - Start: `OLLAMA_HOST=127.0.0.1:11435 ollama serve`
  - Configure: set env `OLLAMA_BASE_URL=http://localhost:11435` (for Docker container use `http://host.docker.internal:11435`).

### Docker Desktop: engine not running
If you see:
- `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.`

Docker’s Linux engine isn’t running. Fix:
1) Start Docker Desktop (ensure the whale icon shows "Running").
2) Open a new PowerShell and verify:
   - `docker --version`
   - `docker compose version`
3) If not found or engine not running:
   - Restart Docker Desktop.
   - Ensure WSL 2 integration is enabled (Settings → Resources → WSL integration) if you use WSL.
   - Sign out/sign in to refresh PATH if the CLI is missing.

After Docker is running:
- Build JARs: `mvn -q -DskipTests package`
- Start stack: `docker compose up --build -d`
- Check services: `docker compose ps`

### Quick verification
- Booking: http://localhost:18081/actuator/health
- Payment: http://localhost:18082/actuator/health
- Assistant: http://localhost:18090/actuator/health

If a container restarts, view logs:
- `docker compose logs --no-color --tail=200 assistant-service`
- `docker compose logs --no-color --tail=200 booking-service`



## Tests: how to run

Prerequisites
- Docker Desktop running in Linux containers mode (needed for Testcontainers/Kafka).
- Java 17+, Maven 3.9+.

Commands
- Run all tests in the repository:
  - PowerShell: `mvn -q test`
- Run tests only for the payment-service module:
  - `mvn -q -pl payment-service -am test`
- Run a single test (example — integration test with Kafka):
  - `mvn -q -pl payment-service -Dtest=PaymentFlowIT test`
- Run only repository (JPA) tests (example):
  - `mvn -q -pl payment-service -Dtest=PaymentRepositoryTest test`

Notes
- On the first run, Testcontainers will pull the Kafka image (Confluent 7.6.0). Internet connection is required.
- If you see `open //./pipe/dockerDesktopLinuxEngine`, start Docker Desktop and ensure Linux containers mode is enabled, then rerun the tests.
- Payment-service integration tests start Kafka automatically via Testcontainers; no external Kafka is needed.

## Interactive Agent Chat (web)

There is a lightweight web chat (static page) that talks to the agent via `/api/assistant/query`.

Option A — via Docker Compose (recommended)
1) Build artifacts: `mvn -q -DskipTests package`
2) Start the stack: `docker compose up --build -d`
3) Open the chat in your browser: `http://localhost:18090/chat.html`
   - Under the hood, the page calls `POST /api/assistant/query` and renders the agent’s replies.
   - In Compose, the assistant connects to Ollama at `http://host.docker.internal:11434`. Make sure Ollama is running and the model is pulled (e.g., `ollama pull llama3.1`).

Option B — run the assistant locally (without Compose)
1) Install and start Ollama:
   - `ollama pull llama3.1`
   - `ollama serve` (port 11434)
2) Start the assistant: `mvn -q -pl assistant-service -am spring-boot:run`
3) Open the chat: `http://localhost:8090/chat.html`
   - By default, the assistant listens on port 8090 (see `assistant-service/src/main/resources/application.yml`).
   - For tools that call other services (Booking/Profile), either run those services locally or via Docker Compose, or override base URLs via env variables:
     - `BOOKING_BASE_URL` (defaults: `http://localhost:18081` outside Docker, `http://booking-service:8081` in Docker)
     - `assistant.tools.profile.base-url` (or an equivalent env var for profile service)

Tips to try
- Example prompts:
  - "Find the cheapest flight from SFO to JFK on 2025-12-24"
  - "Create a booking for user u-123 on trip t-456 with price 99.9"
  - "Show my bookings"
- Agent tools currently include:
  - BookingTools (create/read/update/delete bookings)
  - ProfileLookupTool (fetch profiles from profile-service)
  - FlightSearchTool (stub returning deterministic mock flight data)

Troubleshooting (chat)
- If the chat page shows connection errors:
  - Verify the assistant is running and reachable on 18090 (Docker) or 8090 (local).
  - Ensure Ollama is running and `assistant.ollama.base-url` points to it.
  - On Windows with Docker: start Docker Desktop and ensure it runs Linux containers.
