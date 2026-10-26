// Ajanda Docker gerekir: Testcontainers testleri ve compose smoke asamasi icin.
pipeline {
    agent { label 'docker' }

    options {
        timestamps()
        timeout(time: 40, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        REGISTRY = "${env.REGISTRY ?: 'registry.example.local'}"
        IMAGE = "${REGISTRY}/abone-provisioning"
        TAG = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(8) : env.BUILD_NUMBER}"
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository'
    }

    stages {
        stage('Derleme') {
            steps {
                sh './mvnw -B -ntp -DskipTests package'
            }
        }

        stage('Testler (JUnit 5 + Testcontainers)') {
            steps {
                // Oracle Free + Artemis kapsayicilari Testcontainers tarafindan kaldirilir
                sh './mvnw -B -ntp test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Statik analiz (SonarQube)') {
            steps {
                // 'sonarqube' adli sunucu Jenkins > Manage > System altinda tanimli; token oradan gelir.
                // JaCoCo raporu bir onceki asamada target/site/jacoco altina yazildi.
                withSonarQubeEnv('sonarqube') {
                    sh './mvnw -B -ntp sonar:sonar'
                }
            }
        }

        stage('Kalite kapisi') {
            steps {
                // SonarQube webhook'u Jenkins'e sonucu bildirene kadar bekler; kapi gecmezse derleme durur
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Imaj') {
            steps {
                sh 'docker build -t "$IMAGE:$TAG" .'
            }
        }

        stage('Smoke (compose + k6)') {
            steps {
                sh 'docker compose up -d --wait'
                sh '''
                    docker run --rm --network abone-provisioning_default \
                      -e BASE_URL=http://app:8080 -e SENARYO=smoke \
                      -v "$PWD/perf:/perf:ro" grafana/k6:0.57.0 run --quiet /perf/k6-siparis.js
                '''
            }
            post {
                always {
                    sh 'docker compose logs app > compose-app.log 2>&1 || true'
                    archiveArtifacts artifacts: 'compose-app.log', allowEmptyArchive: true
                    sh 'docker compose down -v'
                }
            }
        }

        stage('Yayinla') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'registry-kimlik',
                        usernameVariable: 'REG_KULLANICI', passwordVariable: 'REG_SIFRE')]) {
                    sh '''
                        echo "$REG_SIFRE" | docker login "$REGISTRY" -u "$REG_KULLANICI" --password-stdin
                        docker push "$IMAGE:$TAG"
                    '''
                }
            }
        }

        stage('Dagitim (Kubernetes / OpenShift)') {
            when { branch 'main' }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-test', variable: 'KUBECONFIG')]) {
                    // OpenShift'te ayni manifestler `oc apply -k` ile uygulanir
                    sh '''
                        kubectl apply -k k8s/
                        kubectl -n abone-provisioning set image statefulset/abone-provisioning \
                          app="$IMAGE:$TAG"
                        kubectl -n abone-provisioning rollout status statefulset/abone-provisioning --timeout=5m
                    '''
                }
            }
        }
    }

    post {
        always {
            cleanWs(deleteDirs: true, notFailBuild: true, patterns: [[pattern: '.m2/**', type: 'EXCLUDE']])
        }
    }
}
