pipeline {

    agent any

    options {
        buildDiscarder(
            logRotator(
                numToKeepStr: '5',
                daysToKeepStr: '90'
            )
        )
        disableConcurrentBuilds()
        timeout(
            time: 30,
            unit: 'MINUTES'
        )
    }

    environment {
        AWS_DEFAULT_REGION = "${AwsRegion}"
        AWS_SVC_DOMAIN = "${AwsSvcDomain}"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
         string(name: 'NotifyEmail', description: 'Email address to send job-status notifications to')
         string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
         string(name: 'AwsSvcDomain',  description: 'Override the CFN service-endpoint DNS domain as necessary')
         string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
         string(name: 'ParmFileS3location', description: 'S3 URL for parameter file (e.g., "s3://<bucket>/<object_key>")')
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
    }

    stages {
        stage ('Push form-vals into Job-Environment') {
            steps {
                // Make sure work-directory is clean //
                deleteDir()

                // Fetch parm-file
                withCredentials(
                    [
                        [
                            $class: 'AmazonWebServicesCredentialsBinding',
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            credentialsId: "${AwsCred}",
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                        ]
                    ]
                ) {
                    sh '''#!/bin/bash
                        aws s3 cp "${ParmFileS3location}" Pipeline.envs
                    '''
                }

                script {
                    def GitCred = sh script:'awk -F "=" \'/GitCred/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.GitCred = GitCred.trim()

                    def GitProjUrl = sh script:'awk -F "=" \'/GitProjUrl/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.GitProjUrl = GitProjUrl.trim()

                    def GitProjBranch = sh script:'awk -F "=" \'/GitProjBranch/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.GitProjBranch = GitProjBranch.trim()

                    def TargetVPC = sh script:'awk -F "=" \'/TargetVPC/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.TargetVPC = TargetVPC.trim()
                }
            }
        }

        stage ('Prep Work Environment') {
            steps {
                // Make sure work-directory is clean //
                deleteDir()

                // More-pedantic SCM declaration to allow use with tags //
                checkout scm: [
                        $class: 'GitSCM',
                        userRemoteConfigs: [
                            [
                                url: "${GitProjUrl}",
                                credentialsId: "${GitCred}"
                            ]
                        ],
                        branches: [
                            [
                                name: "${GitProjBranch}"
                            ]
                        ]
                    ],
                    poll: false

                // Create parameter file to be used with stack-create //

                writeFile file: 'SG.parms.json',
                   text: /
                         [
                             {
                                 "ParameterKey": "TargetVPC",
                                 "ParameterValue": "${env.TargetVPC}"
                             }
                         ]
                   /
                // Create parameter file to be used with stack-create //
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       # Bail on failures
                       set -euo pipefail

                       # For compatibility with ancient AWS CLI utilities
                       if [[ -v ${AWS_SVC_DOMAIN+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url cloudformation.${AWS_SVC_DOMAIN}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Attempting to delete any active ${CfnStackRoot}-SgRes stacks..."
                       ${CFNCMD} delete-stack --stack-name ${CfnStackRoot}-SgRes || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-SgRes \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-SgRes to delete..."
                          sleep 30
                       done
                    '''
                }
            }
        }

        stage ('Launch SecGrp Template') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       # For compatibility with ancient AWS CLI utilities
                       if [[ -v ${AWS_SVC_DOMAIN+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url cloudformation.${AWS_SVC_DOMAIN}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Attempting to create stack ${CfnStackRoot}-SgRes..."
                       ${CFNCMD} create-stack --stack-name ${CfnStackRoot}-SgRes \
                           --template-body file://Templates/make_collibra_SecGrps.tmplt.json \
                           --parameters file://SG.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-SgRes \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-SgRes to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               ${CFNCMD} describe-stacks \
                                 --stack-name ${CfnStackRoot}-SgRes \
                                 --query 'Stacks[].{Status:StackStatus}' \
                                 --out text 2> /dev/null | \
                               grep -q CREATE_COMPLETE
                              )$? -eq 0 ]]
                       then
                          echo "Success. Created:"
                          aws cloudformation describe-stacks --stack-name ${CfnStackRoot}-SgRes \
                            --query 'Stacks[].Outputs[].{Description:Description,Value:OutputValue}' \
                            --output table | sed 's/^/    /' 
                       else
                          echo "Stack-creation ended with non-successful state"
                          exit 1
                       fi
                    '''
                }
            }
        }
    }

    post {
        always {
            deleteDir() /* lets be a good citizen */
        }
        // Emit a failure-email if a notification-address is set
        failure {
            script {
                if ( env.NotifyEmail != '' ) {
                    mail to: "${env.NotifyEmail}",
                        subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
                        body: "Something is wrong with ${env.BUILD_URL}"
                }
            }
        }
    }
}
