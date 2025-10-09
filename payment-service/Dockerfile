# Simple runtime image expecting a pre-built jar under target/
FROM eclipse-temurin:17-jre
ARG JAR=target/payment-service-*.jar
COPY ${JAR} /app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app.jar"]
