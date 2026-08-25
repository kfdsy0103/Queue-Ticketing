# 배포 설정 변수

인프라를 처음부터 다시 세울 때 필요한 값 목록이다. 값 자체는 여기 적지 않는다.

| 저장 위치 | 무엇이 들어가나 | 누가 읽나 |
|---|---|---|
| GitHub Secrets | 이미지 빌드·푸시, 배포 트리거 | `.github/workflows/deploy-*.yml` |
| SSM `/ticketing/prod` | 앱 런타임 환경변수 | `ASG/*/userdata.sh` → `.env` → 컨테이너 |
| SSM `/ticketing/docker` | Docker Hub 로그인 | `ASG/*/userdata.sh` |
| 모니터링 EC2 `.env` | 익스포터 접속 정보 | `monitoring/docker-compose.yml` |
| k6 `.env` | 부하 테스트 파라미터 | `k6/*.js` — [k6/env-example.md](k6/env-example.md) 참고 |

---

## 1. GitHub Secrets

| 이름 | 설명 |
|---|---|
| `DOCKER_USERNAME` | Docker Hub 유저네임 |
| `DOCKER_TOKEN` | Docker Hub 액세스 토큰 |
| `DOCKER_REPOSITORY_TICKETING` | 티켓팅 이미지 리포. `계정명/리포명` |
| `DOCKER_REPOSITORY_QUEUE` | 대기열 이미지 리포. `계정명/리포명` |
| `JENKINS_HOST` | Jenkins 호스트 |
| `JENKINS_PORT` | Jenkins 포트 |
| `JENKINS_BUILD_TOKEN` | Jenkins 액세스 토큰. Build Token Root 플러그인 토큰과 일치시킨다 |
| `JENKINS_JOB_TICKETING` | 티켓팅 파이프라인 이름 |
| `JENKINS_JOB_QUEUE` | 대기열 파이프라인 이름 |

워크플로는 **빌드 잡과 배포 잡으로 나뉜다.** 빌드 잡(jar 빌드 → Docker Hub 푸시)은 `DOCKER_*` 만 쓰고 Jenkins와 무관하다. 배포 잡이 `JENKINS_*` 로 파이프라인을 트리거한다. Jenkins를 쓰지 않는다면 배포 잡만 제거하면 빌드는 그대로 동작한다.

---

## 2. AWS SSM Parameter Store

### 2-1. `/ticketing/prod`

오토스케일링으로 인스턴스가 뜰 때 `userdata.sh` 가 이 경로를 통째로 읽어 `.env` 로 만든다. **파라미터 이름의 마지막 조각이 그대로 환경변수 이름이 된다.**

#### 공용 (두 서버 모두)

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `REDIS_HOST` | String | ✅ | — | Redis 엔드포인트. **포트를 붙이지 않는다** |
| `REDIS_PORT` | String | ✅ | — | 보통 `6379` |
| `JWT_SECRET` | SecureString | ✅ | — | HS256 서명 키. UTF-8 기준 **32바이트 이상** |
| `MONITORING_HOST` | String | ✅ | — | 모니터링 EC2 사설 IP. userdata가 alloy 설정에 치환한다 |
| `DOCKER_REPOSITORY_TICKETING` | String | ✅ | — | compose가 이미지 이름을 조립할 때 쓴다 |
| `DOCKER_REPOSITORY_QUEUE` | String | ✅ | — | 〃 |
| `IMAGE_TAG_TICKETING` | String | ✅ | — | 배포 파이프라인이 커밋 SHA로 갱신한다 |
| `IMAGE_TAG_QUEUE` | String | ✅ | — | 〃 |
| `QUEUE_ACTIVE_TTL_SECONDS` | String | ⬜ | `420` | 작업열(Active) 슬롯 TTL(초) |

#### 티켓팅 서버 전용

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `DB_MASTER_URL` | String | ✅ | — | 쓰기용 엔드포인트 |
| `DB_SLAVE_URL` | String | ✅ | — | 읽기용 엔드포인트 |
| `DB_USERNAME` | String | ✅ | — | |
| `DB_PASSWORD` | SecureString | ✅ | — | |
| `TICKETING_SCHEDULER_QUEUE_PROMOTION_BATCH_SIZE` | String | ⬜ | `15` | 1회 승격 인원 |
| `TICKETING_SCHEDULER_QUEUE_PROMOTION_CRON` | String | ⬜ | `* * * * * *` | 승격 주기 (기본 1초) |

**대기열 서버는 DB 변수를 쓰지 않는다.** datasource·jpa 설정 자체가 없다. 다만 userdata가 경로를 통째로 긁어오므로 `.env` 에는 함께 들어간다. 무시될 뿐이다.

#### 주의사항

- **선택 항목은 코드에 기본값이 있다.** SSM에 없어도 앱은 정상 기동한다. 값을 조정할 때만 파라미터를 만든다.
- **초당 유입량 = 배치 크기 ÷ 승격 주기.** 둘 중 하나만 바꾸면 의도한 유입량이 나오지 않는다. 기본값 기준 15명/초이며, 부하 테스트로 측정한 티켓팅 API TPS를 넘지 않게 잡는다.
- **`QUEUE_ACTIVE_TTL_SECONDS` 는 두 서버에 같은 값이 들어가야 한다.** 티켓팅 서버의 승격 스케쥴러와 대기열 서버의 이어받기가 같은 `activeKey` 에 TTL을 건다. 어긋나면 이어받기 직후 세션이 예상보다 일찍 끊기거나 늦게까지 남는다.
- **CRON 값에는 공백이 들어간다.** SSM 콘솔에 따옴표 없이 그대로 넣는다. userdata가 `.env` 로 옮길 때 그대로 전달된다.

### 2-2. `/ticketing/docker`

| 이름 | 타입 | 설명 |
|---|---|---|
| `DOCKER_USERNAME` | String | 이미지 pull 전 `docker login` 용 |
| `DOCKER_TOKEN` | SecureString | 〃 |

Docker Hub 리포지토리를 private로 만들었기 때문에 필요하다.

---

## 3. 모니터링 EC2 `.env`

`monitoring/docker-compose.yml` 이 읽는 값이다. SSM이 아니라 해당 인스턴스에 직접 `.env` 로 둔다.

| 이름 | 필수 | 설명 |
|---|---|---|
| `RDS_EXPORTER_USERNAME` | ✅ | mysqld-exporter 접속 계정 |
| `RDS_EXPORTER_PASSWORD` | ✅ | 위 계정 비밀번호. 마스터·슬레이브 3개가 공유한다 |
| `RDS_MASTER_EXPORTER_ADDRESS` | ✅ | `host:3306` 형식 |
| `RDS_SLAVE1_EXPORTER_ADDRESS` | ✅ | 〃 |
| `RDS_SLAVE2_EXPORTER_ADDRESS` | ✅ | 〃 |
| `REDIS_EXPORTER_ADDR` | ✅ | `redis://host:6379` 형식 |
| `REDIS_EXPORTER_PASSWORD` | ⬜ | AUTH를 안 쓰면 비워둔다 |

`yace`(CloudWatch 익스포터)는 환경변수를 쓰지 않고 **EC2 인스턴스 역할로 인증**한다. 아래 IAM 항목을 참고한다.

---

## 4. EC2 IAM 역할

서버 성격별로 3개. 관리형 정책 위주로 붙인다.

### 4-1. 앱 EC2 (`ticketing-asg` / `queue-asg`)

| 정책 | 종류 | 이유 |
|---|---|---|
| `AmazonSSMReadOnlyAccess` | 관리형 | `GetParameter` / `GetParametersByPath` 포함 |
| `kms:Decrypt` (Resource `*`) | 인라인 | SecureString 3개 복호화 |

- `AmazonSSMManagedInstanceCore` 에는 Parameter Store 읽기 권한이 없다. Session Manager용이다. 이것만 붙이면 userdata가 `get-parameter` 에서 실패한다.
- `kms:Decrypt` 는 관리형 정책에 없어 인라인으로 따로 붙인다. `JWT_SECRET`·`DB_PASSWORD`·`DOCKER_TOKEN` 이 SecureString이다.
- ECR·CloudWatch 권한은 불필요하다. Docker Hub와 자체 Prometheus·Loki를 쓴다.

### 4-2. Jenkins EC2

| 정책 | 종류 | 이유 |
|---|---|---|
| `AmazonSSMFullAccess` | 관리형 | `put-parameter` 로 이미지 태그 갱신 |
| `AutoScalingFullAccess` | 관리형 | instance-refresh start / cancel / describe |

- Launch Template이 인스턴스 프로파일을 지정하면 `iam:PassRole` 이 요구될 수 있다. 권한 오류가 나면 앱 EC2 역할에 대한 PassRole을 인라인으로 추가한다.

### 4-3. 모니터링 EC2

| 정책 | 종류 | 이유 |
|---|---|---|
| `AmazonEC2ReadOnlyAccess` | 관리형 | `prometheus.yml` 의 `ec2_sd_configs` 가 `DescribeInstances` 를 호출 |
| `CloudWatchReadOnlyAccess` | 관리형 | yace가 RDS·ElastiCache 지표를 CloudWatch에서 가져온다 |
| `ResourceGroupsandTagEditorReadOnlyAccess` | 관리형 | yace가 `tag:GetResources` 로 대상 리소스를 찾는다 |

- **CloudWatch로 스크랩할 리소스에는 태그를 하나 이상 붙여야 한다.** 태그가 없으면 yace가 대상을 식별하지 못한다.

---

## 5. 리소스 이름

| 종류 | 이름 |
|---|---|
| Auto Scaling Group | `ticketing-asg`, `queue-asg` |
| SSM 경로 | `/ticketing/prod`, `/ticketing/docker` |
| 리전 | `ap-northeast-2` |

---

## 6. 콘솔 작업 체크리스트

- [ ] Docker Hub 리포지토리 2개를 private으로 생성
- [ ] GitHub Secrets 9개 등록
- [ ] SSM 파라미터 등록 (`/ticketing/prod`, `/ticketing/docker`)
- [ ] EC2 IAM 역할 3종 생성 후 인스턴스 프로파일 연결
- [ ] Launch Template에 `ASG/ticketing/userdata.sh`, `ASG/queue/userdata.sh` 를 각각 붙여넣기
- [ ] Jenkins Job 2개 생성. 각각 `jenkins/pipeline/*.groovy` 를 가리키고 Build Token Root 토큰을 `JENKINS_BUILD_TOKEN` 과 일치시킨다
- [ ] Jenkins 파이프라인의 `SSM_PATH`·`ASG_NAME`·`LAUNCH_TEMPLATE_ID` 를 채운다 (비어 있으면 Validate 단계에서 중단된다)
- [ ] 모니터링 EC2에 `.env` 배치 후 `docker compose up -d`
- [ ] 보안그룹 — 앱 EC2의 `8080`·`9100` 을 모니터링 EC2에서 접근 가능하게 열기

---

## 7. SSM에 두지 않는 값

코드나 이미지에 박혀 있어 파라미터로 관리하지 않는 것들이다. 찾을 때 헷갈리기 쉬워 남겨둔다.

| 값 | 위치 | 비고 |
|---|---|---|
| `spring.profiles.active=prod` | 각 서버 `Dockerfile` ENTRYPOINT | 그래서 `application-prod.yaml` 만 로드된다 |
| `TZ`, `MALLOC_ARENA_MAX`, `JAVA_TOOL_OPTIONS` | `docker-compose.yml` | 힙·메타스페이스 상한은 여기서 정한다 |
| `ALLOY_ENV` | `docker-compose.yml` | alloy가 로그 라벨로 쓴다 |
| `spring.jwt.expiration` | `application-prod.yaml` | `1800000` 고정 |
| 로그 경로·롤링 정책 | `logback-spring.xml` | 환경변수가 아니라 XML 내 `<property>` |
