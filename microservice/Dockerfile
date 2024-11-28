# Use Zulu JDK 17 compatible with x86
FROM azul/zulu-openjdk:17

# Use x86 emulation with Rosetta
RUN apt-get update && apt-get install -y qemu-user-static

# Set working directory
WORKDIR /app

# Copy the application
COPY target/microservice.jar /app/microservice.jar

# Expose the application port
EXPOSE 8081

# Use Rosetta for runtime emulation
CMD ["java", "-jar", "/app/microservice.jar"]