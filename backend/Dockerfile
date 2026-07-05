# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- run (small JRE, tuned for memory-capped free containers) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
# The host injects $PORT (Cloud Run 8080, Render 10000, …); Spring reads it via server.port=${PORT:8080}.
EXPOSE 8080
# Cap the heap to the container. Override JAVA_TOOL_OPTIONS per host — e.g. -XX:MaxRAMPercentage=55
# on a 512MB free tier, or 75 on a 1GB+ box. The JVM picks up JAVA_TOOL_OPTIONS automatically.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
ENTRYPOINT ["java","-jar","/app/app.jar"]
