# Simple runtime image expecting a pre-built jar under target/
FROM eclipse-temurin:21-jre
ARG JAR=target/profile-service-*.jar
COPY ${JAR} /app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app.jar"]
