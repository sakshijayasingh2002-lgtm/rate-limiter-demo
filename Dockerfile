# Runs the already-built Spring Boot jar. Build the jar first (see the steps):
#   Eclipse: Run As > Maven build... > Goals: clean package -DskipTests
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY target/rate-limiter-demo-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
