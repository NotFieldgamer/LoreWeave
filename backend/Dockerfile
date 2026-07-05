# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- run (small JRE, tuned for Cloud Run's memory-capped containers) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
# Cloud Run injects $PORT (default 8080); Spring reads it via server.port=${PORT:8080}.
EXPOSE 8080
# Cap the heap to the container's memory (Cloud Run gives 256Mi–512Mi on the free tier).
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
