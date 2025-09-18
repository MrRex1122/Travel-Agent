# Travel Agent Project

A monorepository template for microservices (Spring Boot 3, Java 17) with Kafka integration and an assistant service.

The current version includes:
- Multi-module Maven project (parent POM) with modules:
    - common — common classes and constants (Event, Topics)
    - booking-service — REST API for creating reservations and publishing events in Kafka
    - payment-service — consumer of reservation events from Kafka
    - profile-service — profile service stub
    - assistant-service — assistant service stub (LLM)
- Dockerfile for each service
- docker-compose for local stand: Zookeeper, Kafka, and all services
- GitHub Actions (Maven CI)

## Requirements
- JDK 24 (Temurin/Oracle/OpenJDK)
- Maven 3.9+
- Docker and Docker Compose

## Build
- Full build (without tests):
    - PowerShell: `mvn -q -DskipTests package`
- Single module build (with dependencies):
    - Example: `mvn -q -pl booking-service -am -DskipTests package`

## Quick start (Docker Compose)
1. Build artifacts (jars):
    - `mvn -q -DskipTests package`
2. Run the local stand:
    - `docker compose up --build`
3. Services and ports:
    - booking-service: http://localhost:8081
    - payment-service: http://localhost:8082
    - profile-service: http://localhost:8083
    - assistant-service: http://localhost:8090
    - Kafka broker: `kafka:9092` within the docker-compose network, `localhost:9092` from the host is not used by default services


> Note: Docker Compose includes automatic creation of Kafka topics (for local development).

## Kafka configuration
- Booking events topic: `travel.bookings` (see `common/src/main/java/.../Topics.java`)
- Environment variables (docker-compose passes them to services):
    - `SPRING_KAFKA_BOOTSTRAP_SERVERS`: default `kafka:9092` in containers, locally — `localhost:9092`
    - `SPRING_KAFKA_CONSUMER_GROUP` (for payment-service): default `payment-service`
- Basic serializers are configured in `application.yml` services.

## API
### booking-service
- Create a booking (publishes an event to Kafka):
    - POST `http://localhost:8081/api/bookings`
    - Body (JSON):
  ```json
    {
      “userId”: “u-123”,
      “tripId”: “t-456”,
      “price”: 99.9
    }
    ```
    - Response 202 Accepted, example:
  ```json
    {
      “status”: “PUBLISHED”,
      “topic”: “travel.bookings”,
      “key”: “u-123:t-456”
    }
    ```

### payment-service
- Consumes `travel.bookings` events and logs their processing. Example of a log in the container:
    - `[payment-service] Received event: type=BOOKING_CREATED, payload=...`

### Health/Info (Actuator)
- booking-service: `GET http://localhost:8081/actuator/health`, `GET http://localhost:8081/actuator/info`
- payment-service: `GET http://localhost:8082/actuator/health`, `GET http://localhost:8082/actuator/info`
- profile-service/assistant-service: basic configuration, ports are specified above (Actuator is included in some services).


## Running without Docker (locally)
- Kafka: run separately (e.g., via Confluent Platform/Bitnami docker or local installation) and specify `spring.kafka.bootstrap-servers=localhost:9092`.
- Running a single service, example booking-service:
    - `mvn -q -pl booking-service -am spring-boot:run`

## Repository structure
- `pom.xml` — parent POM (Spring Boot 3.3.3 dependency management)
- `common/` — common DTOs and constants (Event, Topics)
- `booking-service/` — REST API and Kafka producer
- `payment-service/` — Kafka consumer
- `profile-service/` — stub
- `assistant-service/` — stub (Web/WebFlux, Actuator)
- `docker-compose.yml` — local stand (Zookeeper, Kafka, services)
- `.github/workflows/build.yml` — CI (Maven build, JDK 17)

## Frequent commands
- Rebuild and restart compose: `docker compose up --build -d`
- View service logs: `docker compose logs -f booking-service`
- Stop: `docker compose down`

## Known limitations and next steps
- assistant-service: LLM client (Ollama) and tools — draft, API calls need to be implemented.
- profile-service: no public API yet.
- Tests (unit/integration) are minimal — plans to add more.
- For production environments, it is recommended to disable auto-creation of topics and use centralized event schema management.
