# Use Zulu JDK 17 compatible with x86
FROM azul/zulu-openjdk:17

# Set working directory
WORKDIR /app

# Copy the microservice's JAR file into the container
COPY target/microservice-1.0.0.jar app.jar

# Expose the application port
EXPOSE 8080

RUN apt-get update && apt-get install -y curl

# Command to run the microservice
ENTRYPOINT ["java", "-jar", "app.jar"]