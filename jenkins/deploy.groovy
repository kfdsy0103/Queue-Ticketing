pipeline {
    agent any

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'GitHub Actions가 넘겨주는 이미지 태그 (커밋 SHA)')
        choice(name: 'TARGET', choices: ['all', 'api', 'queue'], description: 'Refresh할 ASG 선택 (all이면 api → queue 순차 교체)')
    }

    environment {
        AWS_REGION = 'ap-northeast-2'  // ASG가 있는 리전
        SSM_PATH   = ""                // SSM Parameter 경로

        // API 서버
        API_ASG_NAME           = ""  // Auto Scaling Group 이름
        API_LAUNCH_TEMPLATE_ID = ""  // Launch Template ID (lt-xxxxxxxx)

        // 대기열 서버
        QUEUE_ASG_NAME           = ""  // Auto Scaling Group 이름
        QUEUE_LAUNCH_TEMPLATE_ID = ""  // Launch Template ID (lt-xxxxxxxx)
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

                    // API -> QUEUE 순서로 Refresh
                    def targets = (params.TARGET == 'all') ? ['api', 'queue'] : [params.TARGET]
                    env.DEPLOY_TARGETS = targets.join(',')

                    targets.each { role ->
                        def asg = (role == 'api') ? env.API_ASG_NAME : env.QUEUE_ASG_NAME
                        def lt  = (role == 'api') ? env.API_LAUNCH_TEMPLATE_ID : env.QUEUE_LAUNCH_TEMPLATE_ID

                        if (!asg?.trim() || !lt?.trim()) {
                            error("[${role}] ASG_NAME / LAUNCH_TEMPLATE_ID 가 비어 있습니다.")
                        }
                    }

                    echo "배포 대상: ${params.IMAGE_TAG} → ${targets.join(', ')}"
                    currentBuild.displayName = "#${BUILD_NUMBER} - ${params.IMAGE_TAG} (${params.TARGET})"
                }
            }
        }

        // SSM Parameter의 Image Tag 갱신
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
                    for (String role : env.DEPLOY_TARGETS.split(',')) {
                        def asgName = (role == 'api') ? env.API_ASG_NAME : env.QUEUE_ASG_NAME
                        def ltId    = (role == 'api') ? env.API_LAUNCH_TEMPLATE_ID : env.QUEUE_LAUNCH_TEMPLATE_ID

                        echo "════════ [${role}] ${asgName} 교체 시작 ════════"
                        env.CURRENT_ASG = asgName

                        def refreshId = sh(
                                script: """
                                    aws autoscaling start-instance-refresh \
                                        --region "\$AWS_REGION" \
                                        --auto-scaling-group-name "${asgName}" \
                                        --desired-configuration '{"LaunchTemplate":{"LaunchTemplateId":"${ltId}","Version":"\$Latest"}}' \
                                        --preferences '{"MinHealthyPercentage":100,"MaxHealthyPercentage":200}' \
                                        --query 'InstanceRefreshId' \
                                        --output text
                                """,
                                returnStdout: true
                        ).trim()
                        echo "[${role}] Instance Refresh 시작: ${refreshId}"

                        while (true) {
                            def result = sh(
                                    script: """
                                        aws autoscaling describe-instance-refreshes \
                                            --region "\$AWS_REGION" \
                                            --auto-scaling-group-name "${asgName}" \
                                            --instance-refresh-ids "${refreshId}" \
                                            --query 'InstanceRefreshes[0].[Status,PercentageComplete]' \
                                            --output text
                                    """,
                                    returnStdout: true
                            ).trim().split(/\s+/)

                            def status = result[0]
                            echo "[${role}] 교체 진행 상태: ${status} (${result[1]}%)"

                            if (status == 'Successful') { break }
                            if (status in ['Failed', 'Cancelled', 'RollbackFailed']) {
                                error("[${role}] Instance Refresh 실패 — 상태: ${status}")
                            }
                            sleep 15
                        }

                        env.CURRENT_ASG = ''
                        echo "✅ [${role}] 교체 완료"
                    }
                }
            }
        }
    }

    post {
        success {
            echo "배포 성공: ${params.IMAGE_TAG} (${params.TARGET})"
        }
        failure {
            script {
                echo "배포 실패: ${params.IMAGE_TAG} (${params.TARGET})"

                // 파이프라인이 끝나도 교체가 계속 도는 것을 막는다
                if (env.CURRENT_ASG?.trim()) {
                    sh """
                        aws autoscaling cancel-instance-refresh \
                            --region "\$AWS_REGION" \
                            --auto-scaling-group-name "${env.CURRENT_ASG}" || true

                        echo '===== ASG 인스턴스 상태 ====='
                        aws autoscaling describe-auto-scaling-groups \
                            --region "\$AWS_REGION" \
                            --auto-scaling-group-names "${env.CURRENT_ASG}" \
                            --query 'AutoScalingGroups[0].Instances[].[InstanceId,LifecycleState,HealthStatus]' \
                            --output table || true
                    """
                }
                echo "인스턴스 로그는 해당 EC2의 /var/log/cloud-init-output.log 를 확인하세요."
            }
        }
        always {
            cleanWs()
        }
    }
}
