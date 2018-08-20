
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
         string(name: 'GitProjUrl', description: 'SSH URL from which to download the Collibra git project')
         string(name: 'GitProjBranch', description: 'Project-branch to use from the Collibra git project')
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
         string(name: 'FrontedService', description: 'Which DGC component this ELB proxies (DGC|CONSOLE|etc.)')
         string(name: 'BackendTimeout', description: 'How long - in seconds - back-end connection may be idle before attempting session-cleanup')
         string(name: 'ProxyPrettyName', description: 'A short, human-friendly label to assign to the ELB (no capital letters)')
         string(name: 'CollibraInstanceId', defaultValue: '', description: 'ID of the EC2-instance this template should create a proxy for (typically left blank)')
         string(name: 'CollibraListenPort', description: 'Public-facing TCP Port number on which the ELB listens for requests to proxy')
         string(name: 'HaSubnets', description: 'IDs of public-facing subnets in which to create service-listeners')
         string(name: 'CollibraListenerCert', description: 'AWS Certificate Manager Certificate ID to bind to SSL listener')
         string(name: 'CollibraServicePort', description: 'TCP port the Collibra EC2 listens on')
         string(name: 'SecurityGroupIds', description: 'List of security groups to apply to the ELB')
         string(name: 'TargetVPC', description: 'ID of the VPC to deploy cluster nodes into')
    }

    stages {
        stage ('Cleanup Work Environment') {
            steps {
                deleteDir()
                git branch: "${GitProjBranch}",
                    credentialsId: "${GitCred}",
                    url: "${GitProjUrl}"
                writeFile file: 'ELB.parms.json',
                   text: /
                       {
                           "ParameterKey": "BackendTimeout",
                           "ParameterValue": "${env.BackendTimeout}"
                       },
                       {
                           "ParameterKey": "CollibraInstanceId",
                           "ParameterValue": "${env.CollibraInstanceId}"
                       },
                       {
                           "ParameterKey": "CollibraListenPort",
                           "ParameterValue": "${env.CollibraListenPort}"
                       },
                       {
                           "ParameterKey": "CollibraListenerCert",
                           "ParameterValue": "${env.CollibraListenerCert}"
                       },
                       {
                           "ParameterKey": "CollibraServicePort",
                           "ParameterValue": "${env.CollibraServicePort}"
                       },
                       {
                           "ParameterKey": "HaSubnets",
                           "ParameterValue": "${env.HaSubnets}"
                       },
                       {
                           "ParameterKey": "ProxyPrettyName",
                           "ParameterValue": "${env.ProxyPrettyName}"
                       },
                       {
                           "ParameterKey": "SecurityGroupIds",
                           "ParameterValue": "${env.SecurityGroupIds}"
                       },
                       {
                           "ParameterKey": "TargetVPC",
                           "ParameterValue": "${env.TargetVPC}"
                       }
                   /
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       echo "Attempting to delete any active ${CfnStackRoot}-ElbRes-${FrontedService} stacks..."
                       aws cloudformation delete-stack --stack-name ${CfnStackRoot}-ElbRes-${FrontedService} || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
                                     --stack-name ${CfnStackRoot}-ElbRes-${FrontedService} \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-ElbRes-${FrontedService} to delete..."
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
                       echo "Attempting to create stack ${CfnStackRoot}-ElbRes-${FrontedService}..."
                       aws cloudformation create-stack --stack-name ${CfnStackRoot}-ElbRes-${FrontedService} \
                           --template-body file://Templates/make_collibra_SecGrps.tmplt.json \
                           --parameters file://ELB.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
                                     --stack-name ${CfnStackRoot}-ElbRes-${FrontedService} \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-ElbRes-${FrontedService} to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               aws cloudformation describe-stacks \
                                 --stack-name ${CfnStackRoot}-ElbRes-${FrontedService} \
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
