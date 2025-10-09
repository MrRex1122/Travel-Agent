# Simple runtime image expecting a pre-built jar under target/
FROM public.ecr.aws/corretto/corretto:17-alpine-jre
ARG JAR=target/assistant-service-*.jar
COPY ${JAR} /app.jar
EXPOSE 8090
ENTRYPOINT ["java","-jar","/app.jar"]
