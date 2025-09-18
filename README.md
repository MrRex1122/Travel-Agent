# Travel Agent Project

Шаблон монорепозитория для микросервисов (Spring Boot 3, Java 17) с интеграцией Kafka и сервисом-ассистентом.

Текущая версия включает:
- Многомодульный Maven-проект (parent POM) с модулями:
  - common — общие классы и константы (Event, Topics)
  - booking-service — REST API для создания брони и публикация события в Kafka
  - payment-service — потребитель событий бронирования из Kafka
  - profile-service — заготовка сервиса профилей
  - assistant-service — заготовка сервиса ассистента (LLM)
- Dockerfile для каждого сервиса
- docker-compose для локального стенда: Zookeeper, Kafka и все сервисы
- GitHub Actions (Maven CI)

## Требования
- Java 17 (Temurin/Oracle/OpenJDK)
- Maven 3.9+
- Docker и Docker Compose

## Сборка
- Полная сборка (без тестов):
  - PowerShell: `mvn -q -DskipTests package`
- Сборка одного модуля (с зависимостями):
  - Пример: `mvn -q -pl booking-service -am -DskipTests package`

## Быстрый старт (Docker Compose)
1. Соберите артефакты (jar’ы):
   - `mvn -q -DskipTests package`
2. Запустите локальный стенд:
   - `docker compose up --build`
3. Сервисы и порты:
   - booking-service: http://localhost:8081
   - payment-service: http://localhost:8082
   - profile-service: http://localhost:8083
   - assistant-service: http://localhost:8090
   - Kafka брокер: `kafka:9092` внутри сети docker-compose, `localhost:9092` с хоста не используется сервисами по умолчанию

> Примечание: В docker-compose включено авто‑создание топиков Kafka (для локальной разработки).

## Конфигурация Kafka
- Топик событий бронирований: `travel.bookings` (см. `common/src/main/java/.../Topics.java`)
- Переменные окружения (docker-compose передаёт их сервисам):
  - `SPRING_KAFKA_BOOTSTRAP_SERVERS`: по умолчанию `kafka:9092` в контейнерах, локально — `localhost:9092`
  - `SPRING_KAFKA_CONSUMER_GROUP` (для payment-service): по умолчанию `payment-service`
- Базовые сериализаторы настроены в `application.yml` сервисов.

## API
### booking-service
- Создать бронь (публикует событие в Kafka):
  - POST `http://localhost:8081/api/bookings`
  - Body (JSON):
    ```json
    {
      "userId": "u-123",
      "tripId": "t-456",
      "price": 99.9
    }
    ```
  - Ответ 202 Accepted, пример:
    ```json
    {
      "status": "PUBLISHED",
      "topic": "travel.bookings",
      "key": "u-123:t-456"
    }
    ```

### payment-service
- Консьюмит события `travel.bookings` и логирует их обработку. Пример лога в контейнере:
  - `[payment-service] Received event: type=BOOKING_CREATED, payload=...`

### Health/Info (Actuator)
- booking-service: `GET http://localhost:8081/actuator/health`, `GET http://localhost:8081/actuator/info`
- payment-service: `GET http://localhost:8082/actuator/health`, `GET http://localhost:8082/actuator/info`
- profile-service/assistant-service: базовая конфигурация, порты указаны выше (Actuator включён в части сервисов).

## Запуск без Docker (локально)
- Kafka: запустите отдельно (например, через Confluent Platform/Bitnami docker или локальную установку) и укажите `spring.kafka.bootstrap-servers=localhost:9092`.
- Запуск одного сервиса, пример booking-service:
  - `mvn -q -pl booking-service -am spring-boot:run`

## Структура репозитория
- `pom.xml` — родительский POM (управление зависимостями Spring Boot 3.3.3)
- `common/` — общие DTO и константы (Event, Topics)
- `booking-service/` — REST API и Kafka продюсер
- `payment-service/` — Kafka консюмер
- `profile-service/` — заготовка
- `assistant-service/` — заготовка (Web/WebFlux, Actuator)
- `docker-compose.yml` — локальный стенд (Zookeeper, Kafka, сервисы)
- `.github/workflows/build.yml` — CI (сборка Maven, JDK 17)

## Частые команды
- Пересборка и перезапуск compose: `docker compose up --build -d`
- Просмотр логов сервиса: `docker compose logs -f booking-service`
- Остановка: `docker compose down`

## Известные ограничения и дальнейшие шаги
- assistant-service: клиент LLM (Ollama) и инструменты — заготовка, требуется реализация API вызовов.
- profile-service: пока без публичного API.
- Тесты (unit/integration) минимальны — планируется добавить.
- Для прод-среды рекомендуется отказ от авто‑создания топиков и централизованное управление схемами событий.

## Лицензия
MIT (или уточните по требованиям проекта).
