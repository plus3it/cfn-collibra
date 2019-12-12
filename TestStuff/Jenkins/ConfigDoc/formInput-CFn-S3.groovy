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
        AWS_SVC_ENDPOINT = "${AwsSvcEndpoint}"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
         string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
         string(name: 'AwsSvcEndpoint',  description: 'Override the CFN service-endpoint as necessary')
         string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
         string(name: 'ParmFileS3location', description: 'S3 URL for parameter file (e.g., "s3://<bucket>/<object_key>")')
         string(name: 'TemplateS3location', description: 'S3 URL in which to stage CFn Templates (e.g., "s3://<bucket>/<object_key>/")')
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

                    def BackupBucket = sh script:'awk -F "=" \'/BackupBucket=/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BackupBucket = BackupBucket.trim()

                    def BackupBucketInventoryTracking = sh script:'awk -F "=" \'/BackupBucketInventoryTracking/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BackupBucketInventoryTracking = BackupBucketInventoryTracking.trim()

                    def BackupReportingBucket = sh script:'awk -F "=" \'/BackupReportingBucket/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BackupReportingBucket = BackupReportingBucket.trim()

                    def FinalExpirationDays = sh script:'awk -F "=" \'/FinalExpirationDays/{ print $2 }\' Pipeline.envs',
                        returnStdout: true

                    env.FinalExpirationDays = FinalExpirationDays.trim()

                    def RetainIncompleteDays = sh script:'awk -F "=" \'/RetainIncompleteDays/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.RetainIncompleteDays = RetainIncompleteDays.trim()

                    def TierToGlacierDays = sh script:'awk -F "=" \'/TierToGlacierDays/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.TierToGlacierDays = TierToGlacierDays.trim()
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
                writeFile file: 'S3bucket.parms.json',
                   text: /
                         [
                             {
                                 "ParameterKey": "BackupBucket",
                                 "ParameterValue": "${env.BackupBucket}"
                             },
                             {
                                 "ParameterKey": "BackupBucketInventoryTracking",
                                 "ParameterValue": "${env.BackupBucketInventoryTracking}"
                             },
                             {
                                 "ParameterKey": "BackupReportingBucket",
                                 "ParameterValue": "${env.BackupReportingBucket}"
                             },
                             {
                                 "ParameterKey": "FinalExpirationDays",
                                 "ParameterValue": "${env.FinalExpirationDays}"
                             },
                             {
                                 "ParameterKey": "RetainIncompleteDays",
                                 "ParameterValue": "${env.RetainIncompleteDays}"
                             },
                             {
                                 "ParameterKey": "TierToGlacierDays",
                                 "ParameterValue": "${env.TierToGlacierDays}"
                             }
                         ]
                   /

                // Clean up stale AWS resources //
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       # Bail on failures
                       set -euo pipefail

                       # For compatibility with ancient AWS CLI utilities
                       if [[ -v ${AWS_SVC_ENDPOINT+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url cloudformation.${AWS_SVC_ENDPOINT}"
                       else
                          CFNCMD="aws cloudformation"
                       fi 

                       echo "Attempting to delete any active ${CfnStackRoot}-S3Res stacks..."
                       ${CFNCMD} delete-stack --stack-name ${CfnStackRoot}-S3Res || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-S3Res \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-S3Res to delete..."
                          sleep 30
                       done
                    '''
                }
            }
        }

        stage ('Launch Bucket Template') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       # Bail on failures
                       set -euo pipefail

                       # For compatibility with ancient AWS CLI utilities
                       if [[ -v ${AWS_SVC_ENDPOINT+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url ${AWS_SVC_ENDPOINT}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Attempting to create stack ${CfnStackRoot}-S3Res..."
                       ${CFNCMD} create-stack --stack-name ${CfnStackRoot}-S3Res \
                           --template-body file://Templates/make_collibra_S3-bucket.tmplt.json \
                           --parameters file://S3bucket.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-S3Res \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-S3Res to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               ${CFNCMD} describe-stacks \
                                 --stack-name ${CfnStackRoot}-S3Res \
                                 --query 'Stacks[].{Status:StackStatus}' \
                                 --out text 2> /dev/null | \
                               grep -q CREATE_COMPLETE
                              )$? -eq 0 ]]
                       then
                          echo "Success. Created:"
                          aws cloudformation describe-stacks --stack-name ${CfnStackRoot}-S3Res \
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
    }
}
