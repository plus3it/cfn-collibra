
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
         choice(name: 'ProxyForService', choices:'Console\nDGC', description: 'Which DGC component this ELB proxies')
         string(name: 'BackendTimeout', defaultValue: '600', description: 'How long - in seconds - back-end connection may be idle before attempting session-cleanup')
         string(name: 'UserProxyFqdn', description: 'FQDN of name to register within R53 for ELB')
         string(name: 'R53ZoneId', description: 'Route53 ZoneId to create proxy-alias DNS record')
         string(name: 'ElbShortName', description: 'A short, human-friendly label to assign to the ELB (no capital letters)')
         string(name: 'CollibraInstanceId', defaultValue: '', description: 'ID of the EC2-instance this template should create a proxy for (typically left blank)')
         string(name: 'CollibraListenPort', defaultValue: '443', description: 'Public-facing TCP Port number on which the ELB listens for requests to proxy')
         string(name: 'HaSubnets', description: 'Provide a comma-separated list of user-facing subnet IDs in which to create service-listeners')
         string(name: 'CertHostingService', defaultValue: 'IAM', description: 'AWS service containing the certificate to SSL-enable the ELB')
         string(name: 'CollibraListenerCert', description: 'AWS Certificate Manager Certificate ID to bind to SSL listener')
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
                       [
                           {
                               "ParameterKey": "ProxyForService",
                               "ParameterValue": "${env.ProxyForService}"
                           },
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
                               "ParameterKey": "CertHostingService",
                               "ParameterValue": "${env.CertHostingService}"
                           },
                           {
                               "ParameterKey": "CollibraListenerCert",
                               "ParameterValue": "${env.CollibraListenerCert}"
                           },
                           {
                               "ParameterKey": "HaSubnets",
                               "ParameterValue": "${env.HaSubnets}"
                           },
                           {
                               "ParameterKey": "ProxyPrettyName",
                               "ParameterValue": "${env.ElbShortName}"
                           },
                           {
                               "ParameterKey": "SecurityGroupIds",
                               "ParameterValue": "${env.SecurityGroupIds}"
                           },
                           {
                               "ParameterKey": "TargetVPC",
                               "ParameterValue": "${env.TargetVPC}"
                           }
                       ]
                   /
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       echo "Attempting to delete any active ${CfnStackRoot}-R53AliasRes-${ProxyForService} stacks..."
                       aws cloudformation delete-stack --stack-name ${CfnStackRoot}-R53AliasRes-${ProxyForService} || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
                                     --stack-name ${CfnStackRoot}-R53AliasRes-${ProxyForService} \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-R53AliasRes-${ProxyForService} to delete..."
                          sleep 30
                       done

                       echo "Attempting to delete any active ${CfnStackRoot}-ElbRes-${ProxyForService} stacks..."
                       aws cloudformation delete-stack --stack-name ${CfnStackRoot}-ElbRes-${ProxyForService} || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
                                     --stack-name ${CfnStackRoot}-ElbRes-${ProxyForService} \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-ElbRes-${ProxyForService} to delete..."
                          sleep 30
                       done
                    '''
                }
            }
        }
        stage ('Launch ELB Template') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       echo "Attempting to create stack ${CfnStackRoot}-ElbRes-${ProxyForService}..."
                       aws cloudformation create-stack --stack-name ${CfnStackRoot}-ElbRes-${ProxyForService} \
                           --template-body file://Templates/make_collibra_ELBv2.tmplt.json \
                           --parameters file://ELB.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
                                     --stack-name ${CfnStackRoot}-ElbRes-${ProxyForService} \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-ElbRes-${ProxyForService} to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               aws cloudformation describe-stacks \
                                 --stack-name ${CfnStackRoot}-ElbRes-${ProxyForService} \
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
        stage ('Create R53 Alias') {
            steps {
                writeFile file: 'R53alias.parms.json',
                   text: /
                       [
                           {
                               "ParameterKey": "AliasName",
                               "ParameterValue": "${env.UserProxyFqdn}"
                           },
                           {
                               "ParameterKey": "AliasR53ZoneId",
                               "ParameterValue": "${env.R53ZoneId}"
                           },
                           {
                               "ParameterKey": "DependsOnStack",
                               "ParameterValue": "${CfnStackRoot}-ElbRes-${ProxyForService}"
                           }
                       ]
                   /
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       echo "Bind a Route53 Alias to the ELB"
                       aws cloudformation create-stack --stack-name ${CfnStackRoot}-R53AliasRes-${ProxyForService} \
                           --template-body file://Templates/make_collibra_R53-ElbAlias.tmplt.json \
                           --parameters file://R53alias.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
                                     --stack-name ${CfnStackRoot}-R53AliasRes-${ProxyForService} \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-ElbRes-${ProxyForService} to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               aws cloudformation describe-stacks \
                                 --stack-name ${CfnStackRoot}-R53AliasRes-${ProxyForService} \
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
