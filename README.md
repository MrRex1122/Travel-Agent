# Travel Agent Project

Monorepo template for microservices (Spring Boot 3, Java 24) with Kafka integration and an Assistant service.

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
- Java 24+ (Temurin/Oracle/OpenJDK)
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
- Consumes events from `travel.bookings` and logs processing. Example container log:
  - `[payment-service] Received event: type=BOOKING_CREATED, payload=...`

### Health/Info (Actuator)
- booking-service: `GET http://localhost:18081/actuator/health`, `GET http://localhost:18081/actuator/info`
- payment-service: `GET http://localhost:18082/actuator/health`, `GET http://localhost:18082/actuator/info`
- profile-service/assistant-service: basic configuration, ports listed above (Actuator enabled in some services).

## Run without Docker (local)
- Kafka: run separately (e.g., via Confluent Platform/Bitnami docker or local install) and set `spring.kafka.bootstrap-servers=localhost:9092`.
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