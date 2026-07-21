FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/*.jar ticketing.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "/app/ticketing.jar"]
