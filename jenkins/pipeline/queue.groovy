pipeline {
    agent any

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'GitHub Actions가 넘겨주는 이미지 태그 (커밋 SHA)')
    }

    environment {
        AWS_REGION         = 'ap-northeast-2'   // ASG가 있는 리전
        SSM_PATH           = ""                 // SSM Parameter 경로
        IMAGE_TAG_PARAM    = 'IMAGE_TAG_QUEUE'  // Queue 서버의 SSM Parameter Key 값
        ASG_NAME           = ""                 // Auto Scaling Group 이름
        LAUNCH_TEMPLATE_ID = ""                 // Launch Template ID
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        // 입력 파라미터 관련 검증 단계
        stage('Validate') {
            steps {
                script {
                    if (params.IMAGE_TAG?.trim() in [null, '', 'latest']) {
                        echo "IMAGE_TAG가 'latest'입니다. 롤백 추적을 위해 커밋 SHA 사용을 권장합니다."
                    }
                    if (!env.SSM_PATH?.trim()) {
                        error("SSM_PATH가 비어 있습니다.")
                    }
                    if (!env.ASG_NAME?.trim()) {
                        error("ASG_NAME이 비어 있습니다.")
                    }
                    if (!env.LAUNCH_TEMPLATE_ID?.trim()) {
                        error("LAUNCH_TEMPLATE_ID 가 비어 있습니다.")
                    }

                    echo "배포 대상 이미지 태그: ${params.IMAGE_TAG}"
                    echo "배포 대상 ASG 이름: ${env.ASG_NAME}"

                    currentBuild.displayName = "#${BUILD_NUMBER} - ${params.IMAGE_TAG}"
                }
            }
        }

        // SSM Parameter에 있는 Image Tag 갱신
        stage('Update Image Tag') {
            steps {
                sh """
                    aws ssm put-parameter \
                        --region "\$AWS_REGION" \
                        --name "\$SSM_PATH/\$IMAGE_TAG_PARAM" \
                        --value "${params.IMAGE_TAG}" \
                        --type String \
                        --overwrite
                """

                echo "이미지 태그 갱신 완료: ${env.IMAGE_TAG_PARAM} = ${params.IMAGE_TAG}"
            }
        }

        // ASG Refresh
        stage('Instance Refresh') {
            steps {
                script {
                    def asgName = env.ASG_NAME
                    def ltId    = env.LAUNCH_TEMPLATE_ID

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

                    echo "Instance Refresh 트리거 - refreshId: ${refreshId}"

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
                        echo "Refresh 진행 상태: ${status} (${result[1]}%)"

                        if (status == 'Successful') {
                            break
                        }
                        if (status in ['Failed', 'Cancelled', 'RollbackFailed']) {
                            error("Instance Refresh 실패 - 상태: ${status}")
                        }

                        sleep 10
                    }

                    echo "${asgName} Refresh 완료"
                }
            }
        }
    }

    post {
        success {
            echo "배포 성공 - ASG: ${env.ASG_NAME}, Image Tag: ${params.IMAGE_TAG}"
        }
        unsuccessful {
            echo "배포 실패 - ASG: ${env.ASG_NAME}, Image Tag: ${params.IMAGE_TAG}"

            sh """
                aws autoscaling cancel-instance-refresh \
                    --region "\$AWS_REGION" \
                    --auto-scaling-group-name "\$ASG_NAME" || true
            """
        }
        always {
            cleanWs()
        }
    }
}
