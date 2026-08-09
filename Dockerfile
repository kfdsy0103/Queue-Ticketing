FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/*.jar ticketing.jar

EXPOSE 8080

# EC2 2GB
ENTRYPOINT ["java", "-Xms1024m", "-Xmx1024m", "-jar", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "/app/ticketing.jar"]
