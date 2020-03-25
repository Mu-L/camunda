#!/usr/bin/env groovy

boolean slaveDisconnected() {
  return currentBuild.rawBuild.getLog(10000).join('') ==~ /.*(ChannelClosedException|KubernetesClientException|ClosedChannelException|FlowInterruptedException).*/
}

def static MAVEN_DOCKER_IMAGE() { return "maven:3.6.1-jdk-8-slim" }

ES_TEST_VERSION_POM_PROPERTY = "elasticsearch.test.version"
CAMBPM_LATEST_VERSION_POM_PROPERTY = "camunda.engine.version"

static String gCloudAndMavenAgent() {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: services
  imagePullSecrets:
    - name: registry-camunda-cloud-secret
  serviceAccountName: ci-optimize-camunda-cloud
  containers:
  - name: gcloud
    image: google/cloud-sdk:alpine
    imagePullPolicy: Always
    command: ["cat"]
    tty: true
    resources:
      limits:
        cpu: 500m
        memory: 512Mi
      requests:
        cpu: 500m
        memory: 512Mi
  - name: maven
    image: ${MAVEN_DOCKER_IMAGE()}
    command: ["cat"]
    tty: true
    env:
      - name: LIMITS_CPU
        valueFrom:
          resourceFieldRef:
            resource: limits.cpu
      - name: TZ
        value: Europe/Berlin
    resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 1
        memory: 1Gi
"""
}

void buildNotification(String buildStatus) {
  // build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  String buildResultUrl = "${env.BUILD_URL}"
  if(env.RUN_DISPLAY_URL) {
    buildResultUrl = "${env.RUN_DISPLAY_URL}"
  }

  def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
  def body = "See: ${buildResultUrl}"
  def recipients = [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

  emailext subject: subject, body: body, recipientProviders: recipients
}

pipeline {

  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml gCloudAndMavenAgent()
    }
  }

  environment {
    NEXUS = credentials("camunda-nexus")
    REGISTRY = credentials('repository-camunda-cloud')
    NAMESPACE = "${env.JOB_BASE_NAME}-${env.BUILD_ID}"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '50'))
    timestamps()
    timeout(time: 24, unit: 'HOURS')
  }

  stages {
    stage('Retrieve CamBPM and Elasticsearch version') {
      steps {
        container('maven') {
          cloneGitRepo()
          script {
            def mavenProps = readMavenPom().getProperties()
            env.ES_VERSION = params.ES_VERSION ?: mavenProps.getProperty(ES_TEST_VERSION_POM_PROPERTY)
            env.CAMBPM_VERSION = params.CAMBPM_VERSION ?: mavenProps.getProperty(CAMBPM_LATEST_VERSION_POM_PROPERTY)
            env.EVENT_IMPORT_ENABLED = true
          }
        }
      }
    }
    stage('Prepare') {
      steps {
        container('gcloud') {
            sh ("""
                # install jq
                apk add --no-cache jq gettext
                # kubectl
                gcloud components install kubectl --quiet
                
                bash .ci/podSpecs/performanceTests/deploy.sh "${NAMESPACE}" "${REGISTRY_USR}" "${REGISTRY_PSW}" "${SQL_DUMP}" "${ES_VERSION}" "${CAMBPM_VERSION}" "${ES_REFRESH_INTERVAL}" "${EVENT_IMPORT_ENABLED}"
            """)
        }
      }
    }
    stage('ImportTest') {
      steps {
        container('gcloud') {
          sh ("""
                bash .ci/podSpecs/performanceTests/wait-for-import-to-finish.sh "${NAMESPACE}"

                curl -s -X POST 'http://elasticsearch.${NAMESPACE}:9200/_refresh'
                
                # assert expected counts
                # note each call here is followed by `|| true` to not let the whole script fail if the curl call fails due short downtimes of pods
                NUMBER_OF_ACTIVITY_EVENTS=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-camunda-activity-*/_count' | jq '.count') || true
                NUMBER_OF_VARIABLE_UPDATES=\$(curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-variable-update-instance*/_count' | jq '.count') || true
               
                # note: each call here is followed by `|| error=true` to not let the whole script fail if one assert fails
                # a final if block checks if there was an error and will let the script fail
                export EXPECTED_NUMBER_OF_VARIABLES=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_variables | cut -f2 -d':'`
                export EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES=`gsutil ls -L gs://optimize-data/${SQL_DUMP} | grep expected_number_of_activity_instances | cut -f2 -d':'`

                echo "NUMBER_OF_ACTIVITY_EVENTS"
                # we use -ge here as the event index is a forward written log, and entities of the last timestamp might get duplicated
                test "\$NUMBER_OF_ACTIVITY_EVENTS" -ge "\${EXPECTED_NUMBER_OF_ACTIVITY_INSTANCES}" || error=true
                echo "NUMBER_OF_VARIABLE_UPDATES"
                # we use -ge here as the variable update index contains entries for each variable update
                test "\$NUMBER_OF_VARIABLE_UPDATES" -ge "\${EXPECTED_NUMBER_OF_VARIABLES}" || error=true

                #Fail the build if there was an error
                if [ \$error ]
                then 
                  exit -1
                fi
            """)
        }
      }
    }
  }

  post {
    changed {
      buildNotification(currentBuild.result)
    }
    always {
      container('gcloud') {
          sh ("bash .ci/podSpecs/performanceTests/kill.sh \"${NAMESPACE}\"")
      }
      // Retrigger the build if the slave disconnected
      script {
        if (slaveDisconnected()) {
          build job: currentBuild.projectName, propagate: false, quietPeriod: 60, wait: false
        }
      }
    }
  }
}

private void cloneGitRepo() {
  git url: 'git@github.com:camunda/camunda-optimize',
          branch: "${params.BRANCH}",
          credentialsId: 'camunda-jenkins-github-ssh',
          poll: false
}
