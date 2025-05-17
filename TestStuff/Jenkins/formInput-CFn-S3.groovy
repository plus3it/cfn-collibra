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
        string(name: 'AwsSvcDomain',  description: 'Override the service-endpoint DNS-domain as necessary')
        string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
        string(name: 'ParmFileS3location', description: 'S3 URL for parameter file (e.g., "s3://<bucket>/<object_key>")')
        string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
    }

    stages {

        stage ('Cross-stage Env-setup') {
            steps {
                // Make sure work-directory is clean //
                deleteDir()

                // Pull AWS credentials from Jenkins credential-store
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
                    // Pull parameter-file to work-directory
                    sh '''#!/bin/bash
                        aws s3 cp "${ParmFileS3location}" Pipeline.envs
                    '''

                    // Export credentials to rest of stages
                    script {
                        env.AWS_ACCESS_KEY_ID = AWS_ACCESS_KEY_ID
                        env.AWS_SECRET_ACCESS_KEY = AWS_SECRET_ACCESS_KEY
                    }

                    // Set endpoint-override vars as necessary
                    script {
                        if ( env.AwsSvcDomain == '' ) {
                            env.CFNCMD = "aws cloudformation"
                        } else {
                            env.CFNCMD = "aws cloudformation --endpoint-url https://cloudformation.${env.AWS_SVC_DOMAIN}/"
                        }
                    }
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

                    def BucketLoggingDestination = sh script:'awk -F "=" \'/BucketLoggingDestination/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BucketLoggingDestination = BucketLoggingDestination.trim()

                    def BucketSecurityToggle = sh script:'awk -F "=" \'/BucketSecurityToggle/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BucketSecurityToggle = BucketSecurityToggle.trim()

                    def BucketSecurityBlockPublicAcls = sh script:'awk -F "=" \'/BucketSecurityBlockPublicAcls/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BucketSecurityBlockPublicAcls = BucketSecurityBlockPublicAcls.trim()

                    def BucketSecurityBlockPublicPolicy = sh script:'awk -F "=" \'/BucketSecurityBlockPublicPolicy/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BucketSecurityBlockPublicPolicy = BucketSecurityBlockPublicPolicy.trim()

                    def BucketSecurityIgnorePublicAcls = sh script:'awk -F "=" \'/BucketSecurityIgnorePublicAcls/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BucketSecurityIgnorePublicAcls = BucketSecurityIgnorePublicAcls.trim()

                    def BucketSecurityRestrictPublicBuckets = sh script:'awk -F "=" \'/BucketSecurityRestrictPublicBuckets/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BucketSecurityRestrictPublicBuckets = BucketSecurityRestrictPublicBuckets.trim()

                    def ComplianceRetention = sh script:'awk -F "=" \'/ComplianceRetention/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.ComplianceRetention = ComplianceRetention.trim()

                    def EncryptionKeyArn = sh script:'awk -F "=" \'/EncryptionKeyArn/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.EncryptionKeyArn = EncryptionKeyArn.trim()
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
                                "ParameterKey": "BucketLoggingDestination",
                                "ParameterValue": "${env.BucketLoggingDestination}"
                            },
                            {
                                "ParameterKey": "BucketSecurityToggle",
                                "ParameterValue": "${env.BucketSecurityToggle}"
                            },
                            {
                                "ParameterKey": "BucketSecurityBlockPublicAcls",
                                "ParameterValue": "${env.BucketSecurityBlockPublicAcls}"
                            },
                            {
                                "ParameterKey": "BucketSecurityBlockPublicPolicy",
                                "ParameterValue": "${env.BucketSecurityBlockPublicPolicy}"
                            },
                            {
                                "ParameterKey": "BucketSecurityIgnorePublicAcls",
                                "ParameterValue": "${env.BucketSecurityIgnorePublicAcls}"
                            },
                            {
                                "ParameterKey": "BucketSecurityRestrictPublicBuckets",
                                "ParameterValue": "${env.BucketSecurityRestrictPublicBuckets}"
                            },
                            {
                                "ParameterKey": "ComplianceRetention",
                                "ParameterValue": "${env.ComplianceRetention}"
                            },
                            {
                                "ParameterKey": "EncryptionKeyArn",
                                "ParameterValue": "${env.EncryptionKeyArn}"
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
                sh '''#!/bin/bash
                    # Bail on failures
                    set -euo pipefail

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

        stage ('Launch Bucket Template') {
            steps {
                sh '''#!/bin/bash
                    # Bail on failures
                    set -euo pipefail

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
