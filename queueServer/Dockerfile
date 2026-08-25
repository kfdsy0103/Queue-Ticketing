FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/queue.jar queue.jar

# 운영 8080, 로컬은 8081
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "-Dsun.net.inetaddr.ttl=60", "/app/queue.jar"]
