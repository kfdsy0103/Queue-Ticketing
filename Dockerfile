FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/*.jar ticketing.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "/app/ticketing.jar"]
