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
         string(name: 'BackupBucket', description: '(Optional) Name to assign to created bucket')
         string(name: 'BackupBucketInventoryTracking', defaultValue: 'false', description: '')
         string(
             name: 'BackupReportingBucket',
             description: '(Optional) Destination for storing analytics data. Must be provided in ARN format'
         )
         string(
             name: 'FinalExpirationDays',
             defaultValue: '30',
             description: 'Number of days to retain objects before aging them out of the bucket'
         )
         string(
             name: 'RetainIncompleteDays',
             defaultValue: '3',
             description: 'Number of days to retain objects that were not completely uploaded'
         )
         string(
             name: 'TierToGlacierDays',
             defaultValue: '7',
             description: 'Number of days to retain objects in standard storage tier'
         )
         string(
             name: 'BucketLoggingDestination',
             description: '(Optional) Where to log bucket-related activity to'
         )
         choice(
             name: 'BucketSecurityBlockPublicAcls',
             choices:[
                 'true',
                 'false'
             ],
             description: 'Block setting of public ACLs on bucket or objects within it'
         )
         choice(
             name: 'BucketSecurityBlockPublicPolicy',
             choices:[
                 'true',
                 'false'
             ],
             description: 'Prevent setting access policies on bucket that would render it effectively public.'
         )
         choice(
             name: 'BucketSecurityIgnorePublicAcls',
             choices:[
                 'true',
                 'false'
             ],
             description: 'Ignore all public ACLs on a bucket and any objects that it contains'
         )
         choice(
             name: 'BucketSecurityRestrictPublicBuckets',
             choices:[
                 'true',
                 'false'
             ],
             description: 'Restrict cross-account access to bucket'
         )
         string(
             name: 'ComplianceRetention',
             description: '(Optional) The number of years that content must be retained for compliance reasons.'
         )
         string(
             name: 'EncryptionKeyArn',
             description: '(Optional) KMS key-ARN, KMS will be used for bucket-encryption if set to a valid KMS key-ARN; otherwise, generic AES256 will be used'
         )
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
                sh '''#!/bin/bash
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
