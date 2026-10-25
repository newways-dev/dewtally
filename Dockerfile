# One image for both roles (api and worker); the role is chosen with SPRING_PROFILES_ACTIVE.
# Build the jar first: .\mvnw.cmd package -DskipTests
FROM eclipse-temurin:25-jre

WORKDIR /app

# Never run as root inside the container.
RUN useradd --system --uid 1001 dewtally

# XRechnung validator configuration (read from the working directory)
COPY kosit/ kosit/
COPY target/dewtally-0.0.1-SNAPSHOT.jar app.jar

# Uploaded documents live in a volume shared by api and worker.
RUN mkdir -p data/documents && chown -R dewtally /app/data
USER dewtally

# Heap = 75 % of the container's memory limit. On OutOfMemoryError the JVM exits at once:
# Docker restarts the container and the unacknowledged message goes back to the queue.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]