
pipeline {

    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timeout(time: 5, unit: 'MINUTES')
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
         string(name: 'GitProjUrl', description: 'SSH URL from which to download the Sonarqube git project')
         string(name: 'GitProjBranch', description: 'Project-branch to use from the Sonarqube git project')
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
         string(name: 'BackupBucketArn', description: 'ARN of S3 Bucket to host Collibra backups')
         string(name: 'RolePrefix', description: 'Prefix to apply to IAM role to make things a bit prettier (optional)')
    }

    stages {
        stage ('Cleanup Work Environment') {
            steps {
                deleteDir()
                git branch: "${GitProjBranch}",
                    credentialsId: "${GitCred}",
                    url: "${GitProjUrl}"
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
                             }
                         ]
                   /
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       echo "Attempting to delete any active ${CfnStackRoot}-IamRes stacks..."
                       aws cloudformation delete-stack --stack-name ${CfnStackRoot}-IamRes || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
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
        stage ('Launch SecGrp Template') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       echo "Attempting to create stack ${CfnStackRoot}-IamRes..."
                       aws cloudformation create-stack --stack-name ${CfnStackRoot}-IamRes \
                           --capabilities CAPABILITY_NAMED_IAM \
                           --template-body file://Templates/make_collibra_IAM-instance.tmplt.json \
                           --parameters file://IAM.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
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
                               aws cloudformation describe-stacks \
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
    }
}
