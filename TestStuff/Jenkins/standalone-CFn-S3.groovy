
pipeline {

    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timeout(time: 15, unit: 'MINUTES')
    }

    environment {
        AWS_DEFAULT_REGION = "${AwsRegion}"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
         string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
         string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
         string(name: 'GitCred', description: 'Jenkins-stored Git credential with which to execute git commands')
         string(name: 'GitProjUrl', description: 'SSH URL from which to download the Collibra git project')
         string(name: 'GitProjBranch', description: 'Project-branch to use from the Collibra git project')
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
         string(name: 'BackupBucket', description: '(Optional) Name to assign to created bucket')
         string(name: 'BackupBucketInventoryTracking', defaultValue: 'false', description: '')
         string(name: 'BackupReportingBucket', description: '(Optional) Destination for storing analytics data. Must be provided in ARN format')
         string(name: 'FinalExpirationDays', defaultValue: '30', description: 'Number of days to retain objects before aging them out of the bucket')
         string(name: 'RetainIncompleteDays', defaultValue: '3', description: 'Number of days to retain objects that were not completely uploaded')
         string(name: 'TierToGlacierDays', defaultValue: '7', description: 'Number of days to retain objects in standard storage tier')

    }

    stages {
        stage ('Cleanup Work Environment') {
            steps {
                deleteDir()
                git branch: "${GitProjBranch}",
                    credentialsId: "${GitCred}",
                    url: "${GitProjUrl}"
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
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       echo "Attempting to delete any active ${CfnStackRoot}-S3Res stacks..."
                       aws cloudformation delete-stack --stack-name ${CfnStackRoot}-S3Res || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
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
                       echo "Attempting to create stack ${CfnStackRoot}-S3Res..."
                       aws cloudformation create-stack --stack-name ${CfnStackRoot}-S3Res \
                           --template-body file://Templates/make_collibra_S3-bucket.tmplt.json \
                           --parameters file://S3bucket.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
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
                               aws cloudformation describe-stacks \
                                 --stack-name ${CfnStackRoot}-S3Res \
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
    }
}
