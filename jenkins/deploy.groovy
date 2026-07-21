pipeline {
    agent any

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'GitHub Actions가 넘겨주는 이미지 태그 (커밋 SHA)')
    }

    environment {
        IMAGE_NAME  =    // Docker Hub
        APP_HOST    =    // Application 서버 Private IP
        APP_USER    =
        DEPLOY_DIR  =
        HEALTH_URL  =
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 15, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Validate') {
            steps {
                script {
                    if (params.IMAGE_TAG?.trim() in [null, '', 'latest']) {
                        echo "⚠️  IMAGE_TAG가 'latest'입니다. 롤백 추적을 위해 커밋 SHA 사용을 권장합니다."
                    }
                    echo "배포 대상: ${IMAGE_NAME}:${params.IMAGE_TAG} → ${APP_HOST}"
                    currentBuild.displayName = "#${BUILD_NUMBER} - ${params.IMAGE_TAG}"
                }
            }
        }

        stage('Deploy') {
            environment {
                IMAGE_TAG = "${params.IMAGE_TAG}"
            }
            steps {
                withCredentials([
                        string(credentialsId: 'db-url', variable: 'DB_URL'),
                        usernamePassword(credentialsId: 'db-credentials',
                                usernameVariable: 'DB_USERNAME', passwordVariable: 'DB_PASSWORD'),
                        string(credentialsId: 'redis-host', variable: 'REDIS_HOST'),
                        string(credentialsId: 'redis-port', variable: 'REDIS_PORT'),
                        string(credentialsId: 'jwt-secret', variable: 'JWT_SECRET')
                ]) {
                    sshagent(credentials: ['api-server-ssh-key']) {
                        sh '''
                            # Jenkins 워크스페이스에 .env 생성
                            cat > .env << EOF
IMAGE_TAG=$IMAGE_TAG
DB_URL=$DB_URL
DB_USERNAME=$DB_USERNAME
DB_PASSWORD=$DB_PASSWORD
REDIS_HOST=$REDIS_HOST
REDIS_PORT=$REDIS_PORT
JWT_SECRET=$JWT_SECRET
INSTANCE_ID=$APP_HOST
EOF

                            # App 서버로 .env 전송
                            scp -o StrictHostKeyChecking=no .env $APP_USER@$APP_HOST:$DEPLOY_DIR/.env

                            # Jenkins 워크스페이스의 .env 즉시 삭제
                            rm -f .env

                            # 배포 실행
                            ssh -o StrictHostKeyChecking=no $APP_USER@$APP_HOST "
                                set -e
                                cd $DEPLOY_DIR
                                chmod 600 .env
                                docker compose pull
                                docker compose up -d --remove-orphans
                                docker image prune -f
                            "
                        '''
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def ok = false
                    for (int i = 1; i <= 12; i++) {
                        def code = sh(
                                script: "curl -s -o /dev/null -w '%{http_code}' --max-time 3 ${HEALTH_URL} || true",
                                returnStdout: true
                        ).trim()
                        echo "헬스체크 시도 ${i}/12 → HTTP ${code}"
                        if (code == '200') { ok = true; break }
                        sleep 5
                    }
                    if (!ok) {
                        error("헬스체크 실패 — 컨테이너 로그 확인 필요")
                    }
                    echo "✅ 헬스체크 통과"
                }
            }
        }
    }

    post {
        success {
            echo "배포 성공: ${IMAGE_NAME}:${params.IMAGE_TAG}"
        }
        failure {
            echo "배포 실패: ${IMAGE_NAME}:${params.IMAGE_TAG}"
            sshagent(credentials: ['api-server-ssh-key']) {
                sh """
                    ssh -o StrictHostKeyChecking=no ${APP_USER}@${APP_HOST} '
                        cd ${DEPLOY_DIR}
                        echo "===== 컨테이너 상태 ====="
                        docker compose ps
                        echo "===== 최근 로그 100줄 ====="
                        docker compose logs --tail=100 app
                    ' || true
                """
            }
        }
        always {
            cleanWs()
        }
    }
}