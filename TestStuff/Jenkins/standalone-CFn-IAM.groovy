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
        string(name: 'AwsSvcDomain',  description: 'Override the service-endpoint DNS-FQDN as necessary')
        string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
        string(name: 'GitCred', description: 'Jenkins-stored Git credential with which to execute git commands')
        string(name: 'GitProjUrl', description: 'SSH URL from which to download the Collibra git project')
        string(name: 'GitProjBranch', description: 'Project-branch to use from the Collibra git project')
        string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
        string(name: 'BackupBucketArn', description: 'ARN of S3 Bucket to host Collibra backups')
        string(name: 'IamBoundaryName', description: 'Name of the permissions-boundary to apply to the to-be-created IAM role')
        string(name: 'RolePrefix', description: 'Prefix to apply to IAM role to make things a bit prettier (optional)')
        string(name: 'CloudwatchBucketName', description: 'Name of the S3 Bucket hosting the CloudWatch agent archive files')
    }

    stages {
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
                writeFile file: 'IAM.parms.json',
                    text: /
                        [
                            {
                                "ParameterKey": "BackupBucketArn",
                                "ParameterValue": "${env.BackupBucketArn}"
                            },
                            {
                                "ParameterKey": "RolePrefix",
                                "ParameterValue": "${env.RolePrefix}"
                            },
                            {
                                "ParameterKey": "IamBoundaryName",
                                "ParameterValue": "${env.IamBoundaryName}"
                            },
                            {
                                "ParameterKey": "CloudwatchBucketName",
                                "ParameterValue": "${env.CloudwatchBucketName}"
                            }
                        ]
                    /

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

                    sh '''#!/bin/bash
                        echo "Attempting to delete any active ${CfnStackRoot}-IamRes stacks..."
                        ${CFNCMD} delete-stack --stack-name ${CfnStackRoot}-IamRes || true
                        sleep 5

                        # Pause if delete is slow
                        while [[ $(
                                    ${CFNCMD} describe-stacks \
                                      --stack-name ${CfnStackRoot}-IamRes \
                                      --query 'Stacks[].{Status:StackStatus}' \
                                      --out text 2> /dev/null | \
                                    grep -q DELETE_IN_PROGRESS
                                    )$? -eq 0 ]]
                        do
                            echo "Waiting for stack ${CfnStackRoot}-IamRes to delete..."
                            sleep 30
                        done
                    '''
                }
            }
        }
        stage ('Launch IAM Template') {
            steps {
                sh '''#!/bin/bash
                    echo "Attempting to create stack ${CfnStackRoot}-IamRes..."
                    ${CFNCMD} create-stack --stack-name ${CfnStackRoot}-IamRes \
                        --capabilities CAPABILITY_NAMED_IAM \
                        --template-body file://Templates/make_collibra_IAM-instance.tmplt.json \
                        --parameters file://IAM.parms.json
                    sleep 5

                    # Pause if create is slow
                    while [[ $(
                                ${CFNCMD} describe-stacks \
                                  --stack-name ${CfnStackRoot}-IamRes \
                                  --query 'Stacks[].{Status:StackStatus}' \
                                  --out text 2> /dev/null | \
                                grep -q CREATE_IN_PROGRESS
                                )$? -eq 0 ]]
                    do
                        echo "Waiting for stack ${CfnStackRoot}-IamRes to finish create process..."
                        sleep 30
                    done

                    if [[ $(
                            ${CFNCMD} describe-stacks \
                              --stack-name ${CfnStackRoot}-IamRes \
                              --query 'Stacks[].{Status:StackStatus}' \
                              --out text 2> /dev/null | \
                            grep -q CREATE_COMPLETE
                            )$? -eq 0 ]]
                    then
                        echo "Stack-creation successful"
                    else
                        echo "Stack-creation ended with non-successful state"
                        exit 1
                    fi
                '''
            }
        }
    }

    // Do after job-stages end
    post {
        // Clean up work-dir no matter what
        always {
            deleteDir()
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
