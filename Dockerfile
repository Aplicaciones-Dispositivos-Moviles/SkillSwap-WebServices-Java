# Build: Maven + JDK 21. Tests are skipped here: they need Docker (Testcontainers) and run before every push.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -q -DskipTests package

# Run: only the JRE, as a non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 skillswap
COPY --from=build /app/target/skillswap-platform-*.jar app.jar
USER skillswap

# Small instances (512 MB): leave room for the JVM itself, and let the platform restart it if it runs out of memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# The port comes from the PORT variable of the platform (server.port=${PORT:8080}).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
