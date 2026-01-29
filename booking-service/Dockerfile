# Simple runtime image expecting a pre-built jar under target/
FROM eclipse-temurin:21-jre
ARG JAR=target/booking-service-*.jar
COPY ${JAR} /app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app.jar"]
