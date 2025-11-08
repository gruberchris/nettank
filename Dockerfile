# Build stage - Using OpenJDK 21 (Java 21)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Create a minimal multi-module structure with just the modules we need
COPY pom.xml ./

# Create temporary minimal pom that only includes server and common modules
RUN sed -i '/<module>nettank-client<\/module>/d' pom.xml

# Copy module pom files
COPY nettank-server/pom.xml ./nettank-server/
COPY nettank-common/pom.xml ./nettank-common/

# Download dependencies (separate layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY nettank-common/src ./nettank-common/src
COPY nettank-server/src ./nettank-server/src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage - Using OpenJDK 21 (Java 21) JRE
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create a non-root user and group
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Server port
EXPOSE 5555

# Copy the built JAR file from the build stage
COPY --from=build /app/nettank-server/target/nettank-server*.jar /app/nettank-server.jar

# Set ownership of the application files
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Set default JVM options
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Default server configuration
ENV SERVER_PORT=5555
ENV NETWORK_HZ=30
ENV MAP_WIDTH=50
ENV MAP_HEIGHT=50

# Run the application using JSON format for ENTRYPOINT
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/nettank-server.jar ${SERVER_PORT:-5555} ${NETWORK_HZ:-30} ${MAP_WIDTH:-50} ${MAP_HEIGHT:-50}"]