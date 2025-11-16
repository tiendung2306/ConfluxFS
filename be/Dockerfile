FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy project files
COPY pom.xml .
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/crdt-file-system-1.0.0.jar"]