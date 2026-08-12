#!/bin/bash
# [대기열 서버용] ASG가 새 EC2를 띄울 때 실행되어, SSM에서 설정을 받아 애플리케이션을 가동하는 스크립트
# 이 파일은 Launch Template의 User data에 그대로 붙여넣어 사용한다.
#
# 아래 docker-compose.yml과 config.alloy는 저장소 루트의 동명 파일을 복사한 것이므로,
# 원본을 수정하면 이 스크립트도 함께 고쳐야 한다.
#
# ASG/api/userdata.sh와는 '4. 역할별 설정' 블록만 다르다. 그 외를 고칠 때는 양쪽에 함께 반영한다.
#
# userdata.sh 바뀌면 Launch Template new version 만들고 젠킨스로 재배포하면 됨
set -euo pipefail

REGION="ap-northeast-2"
SSM_PATH="/ticketing/prod"
DOCKER_SSM_PATH="/ticketing/docker"   # 레지스트리 인증 정보 (.env에 섞이지 않도록 경로를 분리)
DEPLOY_DIR="/app"
MONITORING_HOST=""   # 모니터링 서버 주소 (private)
COMPOSE_VERSION="v2.39.1"

# ── 1. Docker, Compose 플러그인, aws CLI 설치 (AMI에 이미 있으면 건너뜀)
export DEBIAN_FRONTEND=noninteractive

apt-get -o DPkg::Lock::Timeout=180 update 2>&1 | tee /tmp/apt-update.log

# apt-get update 실패 에러
if grep -q '^Err:' /tmp/apt-update.log; then
    echo "[userdata] apt update failed." >&2
    grep '^Err:' /tmp/apt-update.log >&2
    exit 1
fi

apt-get -o DPkg::Lock::Timeout=180 install -y docker.io unzip
systemctl enable --now docker

PLUGIN_DIR="/usr/local/lib/docker/cli-plugins"
if [ ! -f "$PLUGIN_DIR/docker-compose" ]; then
    mkdir -p "$PLUGIN_DIR"
    curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
        -o "$PLUGIN_DIR/docker-compose"
    chmod +x "$PLUGIN_DIR/docker-compose"
fi

# Ubuntu에는 aws CLI가 기본 탑재되어 있지 않다
if ! command -v aws > /dev/null; then
    curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
    unzip -q /tmp/awscliv2.zip -d /tmp
    /tmp/aws/install
    rm -rf /tmp/aws /tmp/awscliv2.zip
fi

# ── 2. 배포 디렉토리와 설정 파일 생성
mkdir -p "$DEPLOY_DIR/alloy" "$DEPLOY_DIR/logs"
cd "$DEPLOY_DIR"

cat > docker-compose.yml <<'COMPOSE_EOF'
services:
  app:
    image: ${DOCKER_REPOSITORY}:${IMAGE_TAG}
    container_name: ticketing-api
    env_file:
      - .env
    ports:
      - "8080:8080"
    volumes:
      - ./logs:/app/logs  # EC2에 로그 파일 마운트 (alloy도 동일한 바인드 마운트해야함에 유의)
    restart: unless-stopped

  alloy:
    image: grafana/alloy:latest
    container_name: alloy
    environment:
      - ALLOY_ENV=prod  # alloy config에서 운영 환경을 인식하고 라벨로 활용하기 위함
    ports:
      - "12345:12345"
    volumes:
      - ./alloy/config.alloy:/etc/alloy/config.alloy:ro   # alloy config 마운트
      - ./logs:/var/log/spring:ro   # spring에서 logback이 남기는 파일 로그를 읽기 위함 (app도 ./logs로 바인드 마운트했음에 유의)
    restart: unless-stopped

  node-exporter:
    depends_on:
      - app
    image: prom/node-exporter:latest
    container_name: node-exporter
    restart: unless-stopped
    pid: host # 컨테이너 내부가 아닌, 호스트 레벨의 프로세스를 보도록
    network_mode: host # 네트워크도 호스트 레벨의 네트워크를 따르도록
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    command:
      - "--path.procfs=/host/proc"
      - "--path.sysfs=/host/sys"
      - "--path.rootfs=/rootfs"
      - "--collector.filesystem.mount-points-exclude=^/(sys|proc|dev|host|etc)($$|/)"
COMPOSE_EOF

cat > alloy/config.alloy <<'ALLOY_EOF'
// 여기에서의 로그 레벨은 alloy 프로세스 자체적인 로그 레벨을 의미
logging {
	level  = "info"
	format = "logfmt"
}

// 1. 로그 파일 경로 지정 (현재는 all.log 하나로 통합)
local.file_match "spring_logs" {
	path_targets = [{ __path__ = "/var/log/spring/all.log" }]
}

// 2. 로그 읽기 및 전달 파이프라인 생성
loki.source.file "spring_source" {
	targets    = local.file_match.spring_logs.targets   // from
	forward_to = [loki.process.spring_labels.receiver]  // to
}

// 3. 로그 가공 및 라벨링
loki.process "spring_labels" {
    forward_to = [loki.write.grafana_loki.receiver]

    // 고정 라벨 설정 (서비스명, 운영 환경)
    stage.static_labels {
       values = {
          service = "ticketing-queue",
          env     = sys.env("ALLOY_ENV"),
       }
    }

    // Java 스택 트레이스 멀티 라인 병합
    stage.multiline {
       firstline     = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"
       max_wait_time = "3s"
    }

    // 로그 라인에서 로그 레벨(level) 추출
    stage.regex {
       expression = "^\\S+ \\S+ \\[(?P<thread>[^\\]]+)\\] (?P<level>\\w+)\\s+"
    }

    // 위에서 추출한 level 라벨링 하여 검색/필터링에 사용
    stage.labels {
       values = {
          level = "",
       }
    }
}

// 4. 모니터링 서버의 Loki로 전송
loki.write "grafana_loki" {
	endpoint {
		url        = "http://__MONITORING_HOST__:3100/loki/api/v1/push"

        tenant_id  = "fake" // Loki 하나를 여러 팀에서 같이 쓰는 경우, 로그를 분리하기 위한 ID 값 (싱글 테넌트이기 때문에 fake로 명시)

		// 1MB에 도달하거나, 1초 지나면 전송
		batch_wait = "1s"
		batch_size = "1MB"
	}
}
ALLOY_EOF

# 정규식의 백슬래시가 깨지지 않도록 따옴표 heredoc으로 쓴 뒤, 모니터링 주소만 치환한다
sed -i "s|__MONITORING_HOST__|${MONITORING_HOST}|" alloy/config.alloy

# ── 3. SSM에서 설정을 받아 .env 생성
# 파라미터 이름을 환경변수명 그대로 두었으므로, 경로의 마지막 조각만 잘라내면 된다
aws ssm get-parameters-by-path \
    --path "$SSM_PATH" \
    --with-decryption \
    --region "$REGION" \
    --query 'Parameters[].[Name,Value]' \
    --output text \
    | awk -F'\t' '{ n = split($1, path, "/"); print path[n] "=" $2 }' > .env

# ── 4. 역할별 설정
# SSM 경로를 api/queue가 공유하므로 역할 값은 SSM이 아니라 여기서 덧붙인다.
# SCHEDULER_ENABLED=false — 스케줄러는 DB를 읽으므로 api 서버가 전담한다. 대기열 서버는 Redis만 쓴다.
# SERVER_TOMCAT_THREADS_MAX — status 폴링은 요청이 짧아 스레드 회전이 빠르므로 기본 200보다 늘려 잡는다.
# MANAGEMENT_HEALTH_DB_ENABLED=false — 대기열은 DB 없이 동작므로
#
# 아래 DataSource/JPA 설정은 대기열 서버가 DB 커넥션을 아예 잡지 않게 하기 위한 것이다.
# DataSourceConfiguration이 역할과 무관하게 master/slave 풀을 만들기 때문에, 그대로 두면 쓰지도 않는 커넥션을 6개 물고 있고
# 부팅 때 DB에 붙느라 DB 장애 시 대기열 서버까지 기동에 실패한다.
#   MINIMUM_IDLE=0                 — 유휴 커넥션을 유지하지 않는다
#   INITIALIZATION_FAIL_TIMEOUT=-1 — 음수면 기동 시 연결 시도 자체를 건너뛴다
#   DDL_AUTO=none                  — 스키마 변경은 api 서버가 전담한다
#   DATABASE_PLATFORM / ALLOW_JDBC_METADATA_ACCESS — Hibernate가 dialect를 알아내려 커넥션을 여는 것을 막는다
cat >> .env <<'ROLE_EOF'
APP_ROLE=queue
SCHEDULER_ENABLED=false
MANAGEMENT_HEALTH_DB_ENABLED=false
SPRING_DATASOURCE_MASTER_MINIMUM_IDLE=0
SPRING_DATASOURCE_MASTER_INITIALIZATION_FAIL_TIMEOUT=-1
SPRING_DATASOURCE_SLAVE_MINIMUM_IDLE=0
SPRING_DATASOURCE_SLAVE_INITIALIZATION_FAIL_TIMEOUT=-1
SPRING_JPA_HIBERNATE_DDL_AUTO=none
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MySQLDialect
SPRING_JPA_PROPERTIES_HIBERNATE_BOOT_ALLOW_JDBC_METADATA_ACCESS=false
ROLE_EOF

chmod 600 .env

# ── 5. 애플리케이션 기동
# private 저장소이므로 pull 전에 로그인하고, 끝나면 자격 증명을 지운다
DOCKER_USERNAME=$(aws ssm get-parameter --region "$REGION" \
    --name "$DOCKER_SSM_PATH/DOCKER_USERNAME" \
    --query 'Parameter.Value' --output text)
DOCKER_TOKEN=$(aws ssm get-parameter --region "$REGION" \
    --name "$DOCKER_SSM_PATH/DOCKER_TOKEN" --with-decryption \
    --query 'Parameter.Value' --output text)

echo "$DOCKER_TOKEN" | docker login --username "$DOCKER_USERNAME" --password-stdin
docker compose pull
docker logout

docker compose up -d --remove-orphans
