# Use Azul Zulu OpenJDK 11 as the base image
FROM azul/zulu-openjdk:11

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

# Build and install the staccato-collections parent POM
WORKDIR $APP_HOME/staccato-collections
RUN $APP_HOME/mvnw clean install -N -DskipTests

# Build staccato-commons module
WORKDIR $APP_HOME/staccato-commons
RUN $APP_HOME/mvnw clean install -DskipTests

# Build staccato-elasticsearch module
WORKDIR $APP_HOME/staccato-elasticsearch
RUN $APP_HOME/mvnw clean install -DskipTests

# Build staccato-collections/landsat8 submodule
WORKDIR $APP_HOME/staccato-collections/landsat8
RUN $APP_HOME/mvnw clean install -DskipTests

# Build staccato-collections/planet submodule
WORKDIR $APP_HOME/staccato-collections/planet
RUN $APP_HOME/mvnw clean install -DskipTests

# Build staccato-application module
WORKDIR $APP_HOME/staccato-application
RUN $APP_HOME/mvnw clean install -DskipTests

# Set the application to run
WORKDIR $APP_HOME/staccato-application/target
ENTRYPOINT ["java", "-jar", "staccato-1.0.0.jar"]