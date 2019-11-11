pipeline {

    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timeout(time: 15, unit: 'MINUTES')
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
         string(name: 'GitCred', description: 'Jenkins-stored Git credential with which to execute git commands')
         string(name: 'GitProjUrl', description: 'SSH URL from which to download the Collibra git project')
         string(name: 'GitProjBranch', description: 'Project-branch to use from the Collibra git project')
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
         string(name: 'TargetVPC', description: 'ID of the VPC to deploy cluster nodes into')
    }

    stages {
        stage ('Cleanup Work Environment') {
            steps {
                deleteDir()
                git branch: "${GitProjBranch}",
                    credentialsId: "${GitCred}",
                    url: "${GitProjUrl}"
                writeFile file: 'SG.parms.json',
                   text: /
                         [
                             {
                                 "ParameterKey": "TargetVPC",
                                 "ParameterValue": "${env.TargetVPC}"
                             }
                         ]
                   /
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       # For compatibility with ancient AWS CLI utilities
                       if [[ -z ${AWS_SVC_ENDPOINT} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url ${AWS_SVC_ENDPOINT}"
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
                       if [[ -z ${AWS_SVC_ENDPOINT} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url ${AWS_SVC_ENDPOINT}"
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

    post {
        always {
            deleteDir() /* lets be a good citizen */
        }
    }
}
