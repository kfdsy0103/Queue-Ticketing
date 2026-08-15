FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/ticketing.jar ticketing.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xms512m", "-Xmx512m", "-jar", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "-Dsun.net.inetaddr.ttl=60", "/app/ticketing.jar"]
