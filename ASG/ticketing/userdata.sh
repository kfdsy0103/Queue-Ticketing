#!/bin/bash
# docker-compose.yml -> ticketingServer/docker-compose.yml
# alloy -> ticketingServer/alloy/config.alloy
# 그대로 복사한 것이므로, 원본을 수정하면 해당 스크립트도 수정 필요 + Launch Template new version 이후 재배포

set -euo pipefail

REGION="ap-northeast-2"             # Region
SSM_PATH="/ticketing/prod"          # AWS Parameter 환경 변수 정보
DOCKER_SSM_PATH="/ticketing/docker" # AWS Parameter Docker 관련 정보
DEPLOY_DIR="/app"                   # 실행 경로

export DEBIAN_FRONTEND=noninteractive
echo 'DPkg::Lock::Timeout "180";' > /etc/apt/apt.conf.d/99-lock-timeout

# apt update
apt-get update
apt-get install -y ca-certificates curl unzip

# Docker
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list

# Docker compose plugin
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable --now docker

# AWS CLI
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q -o /tmp/awscliv2.zip -d /tmp
/tmp/aws/install --update
rm -rf /tmp/aws /tmp/awscliv2.zip

# 실행 경로 설정
mkdir -p "$DEPLOY_DIR/alloy" "$DEPLOY_DIR/logs"
cd "$DEPLOY_DIR"

# docker-compose
cat > docker-compose.yml <<'COMPOSE_EOF'
services:
  app:
    image: ${DOCKER_REPOSITORY_TICKETING}:${IMAGE_TAG_TICKETING}
    container_name: ticketing
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

# alloy config
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
          service = "ticketing",
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

# alloy config에 모니터링 서버 주소(private IP) 치환
MONITORING_HOST=$(aws ssm get-parameter --region "$REGION" \
    --name "$SSM_PATH/MONITORING_HOST" \
    --query 'Parameter.Value' --output text)

sed -i "s|__MONITORING_HOST__|${MONITORING_HOST}|" alloy/config.alloy

# .env 설정 (KEY=VALUE)
aws ssm get-parameters-by-path \
    --path "$SSM_PATH" \
    --with-decryption \
    --region "$REGION" \
    --query 'Parameters[].[Name,Value]' \
    --output text \
    | awk -F'\t' '{ n = split($1, path, "/"); print path[n] "=" $2 }' > .env

chmod 600 .env

# Docker Login
DOCKER_USERNAME=$(aws ssm get-parameter --region "$REGION" \
    --name "$DOCKER_SSM_PATH/DOCKER_USERNAME" \
    --query 'Parameter.Value' --output text)
DOCKER_TOKEN=$(aws ssm get-parameter --region "$REGION" \
    --name "$DOCKER_SSM_PATH/DOCKER_TOKEN" --with-decryption \
    --query 'Parameter.Value' --output text)

echo "$DOCKER_TOKEN" | docker login --username "$DOCKER_USERNAME" --password-stdin
docker compose pull
docker logout

# Application run
docker compose up -d --remove-orphans
