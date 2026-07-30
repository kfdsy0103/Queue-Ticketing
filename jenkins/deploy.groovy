pipeline {
    agent any

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'GitHub Actions가 넘겨주는 이미지 태그 (커밋 SHA)')
    }

    environment {
        AWS_REGION =  'ap-northeast-2'  // ASG가 있는 리전
        ASG_NAME   =  ""  // Application Auto Scaling Group 이름
        SSM_PATH   = ""     // SSM Parameter 경로
        HEALTH_URL = ""   // ALB의 헬스체크 엔드포인트
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Validate') {
            steps {
                script {
                    if (params.IMAGE_TAG?.trim() in [null, '', 'latest']) {
                        echo "⚠️  IMAGE_TAG가 'latest'입니다. 롤백 추적을 위해 커밋 SHA 사용을 권장합니다."
                    }
                    echo "배포 대상: ${params.IMAGE_TAG} → ${ASG_NAME}"
                    currentBuild.displayName = "#${BUILD_NUMBER} - ${params.IMAGE_TAG}"
                }
            }
        }

        stage('Image Tag 갱신') {
            environment {
                IMAGE_TAG = "${params.IMAGE_TAG}"
            }
            steps {
                sh '''
                    aws ssm put-parameter \
                        --region "$AWS_REGION" \
                        --name "$SSM_PATH/IMAGE_TAG" \
                        --value "$IMAGE_TAG" \
                        --type String \
                        --overwrite
                '''
            }
        }

        // ASG Refresh
        stage('Instance Refresh') {
            steps {
                script {
                    env.REFRESH_ID = sh(
                            script: '''
                                aws autoscaling start-instance-refresh \
                                    --region "$AWS_REGION" \
                                    --auto-scaling-group-name "$ASG_NAME" \
                                    --preferences '{"MinHealthyPercentage":100,"MaxHealthyPercentage":200}' \
                                    --query 'InstanceRefreshId' \
                                    --output text
                            ''',
                            returnStdout: true
                    ).trim()
                    echo "Instance Refresh 시작: ${env.REFRESH_ID}"
                }
            }
        }

        stage('Wait For Refresh') {
            steps {
                script {
                    while (true) {
                        def result = sh(
                                script: '''
                                    aws autoscaling describe-instance-refreshes \
                                        --region "$AWS_REGION" \
                                        --auto-scaling-group-name "$ASG_NAME" \
                                        --instance-refresh-ids "$REFRESH_ID" \
                                        --query 'InstanceRefreshes[0].[Status,PercentageComplete]' \
                                        --output text
                                ''',
                                returnStdout: true
                        ).trim().split(/\s+/)

                        def status = result[0]
                        echo "교체 진행 상태: ${status} (${result[1]}%)"

                        if (status == 'Successful') { break }
                        if (status in ['Failed', 'Cancelled', 'RollbackFailed']) {
                            error("Instance Refresh 실패 — 상태: ${status}")
                        }
                        sleep 15
                    }
                    echo "✅ 인스턴스 교체 완료"
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def ok = false
                    for (int i = 1; i <= 12; i++) {
                        def code = sh(
                                script: '''curl -s -o /dev/null -w '%{http_code}' --max-time 3 $HEALTH_URL || true''',
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
            echo "배포 성공: ${params.IMAGE_TAG}"
        }
        failure {
            echo "배포 실패: ${params.IMAGE_TAG}"
            // 파이프라인이 끝나도 교체가 계속 도는 것을 막는다
            sh '''
                aws autoscaling cancel-instance-refresh \
                    --region "$AWS_REGION" \
                    --auto-scaling-group-name "$ASG_NAME" || true

                echo '===== ASG 인스턴스 상태 ====='
                aws autoscaling describe-auto-scaling-groups \
                    --region "$AWS_REGION" \
                    --auto-scaling-group-names "$ASG_NAME" \
                    --query 'AutoScalingGroups[0].Instances[].[InstanceId,LifecycleState,HealthStatus]' \
                    --output table || true
            '''
            echo "인스턴스 로그는 해당 EC2의 /var/log/cloud-init-output.log 를 확인하세요."
        }
        always {
            cleanWs()
        }
    }
}
