FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/ticketing.jar ticketing.jar

EXPOSE 8080

# 메모리 옵션(-Xms/-Xmx/메타스페이스/다이렉트/스택)은 여기 두지 않는다.
# 이미지에 박으면 값 하나 바꾸는 데 재빌드가 필요해서 docker-compose.yml 의
# JAVA_TOOL_OPTIONS 로 옮겼다. 예산 배분 근거는 그쪽 주석에 있다.
# JAVA_TOOL_OPTIONS 는 커맨드라인 앞에 붙고 HotSpot 은 뒤에 온 값이 이기므로,
# 여기에 -Xmx 를 다시 넣으면 환경변수 쪽이 조용히 무시된다.
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "-Dsun.net.inetaddr.ttl=60", "/app/ticketing.jar"]
