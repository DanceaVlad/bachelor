# Use Azul Zulu OpenJDK 11 as the base image
FROM azul/zulu-openjdk:17

ENV APP_HOME=/app
WORKDIR $APP_HOME

# Copy the project files into the container
COPY . $APP_HOME

RUN apt-get update && apt-get install -y curl

# Ensure Maven wrapper is executable
RUN chmod +x $APP_HOME/mvnw

# Build and install the parent POM
WORKDIR $APP_HOME
RUN $APP_HOME/mvnw clean install -N -DskipTests

# Expose the application port
EXPOSE 8080

# Command to run the microservice
ENTRYPOINT ["java", "-jar", "app.jar"]

# Set the application to run
WORKDIR $APP_HOME/target
ENTRYPOINT ["java", "-jar", "microservice-1.0.0.jar"]