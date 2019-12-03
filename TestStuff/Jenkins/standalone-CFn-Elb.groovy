pipeline {

    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timeout(time: 15, unit: 'MINUTES')
    }

    environment {
        AWS_DEFAULT_REGION = "${AwsRegion}"
        AWS_CFN_ENDPOINT = "${AwsCfnEndpoint}"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
         string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
         string(name: 'AwsCfnEndpoint',  description: 'Override the CFN-endpoint as necessary')
         string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
         string(name: 'GitCred', description: 'Jenkins-stored Git credential with which to execute git commands')
         string(name: 'GitProjUrl', description: 'SSH URL from which to download the Collibra git project')
         string(name: 'GitProjBranch', description: 'Project-branch to use from the Collibra git project')
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
         choice(name: 'ProxyForService', choices:[ 'Console', 'DGC' ], description: 'Which DGC component this ELB proxies')
         string(name: 'UserProxyFqdn', description: 'FQDN of name to register within R53 for ELB')
         string(name: 'R53ZoneId', description: 'Route53 ZoneId to create proxy-alias DNS record')
         string(name: 'ElbShortName', description: 'A short, human-friendly label to assign to the ELB (no capital letters)')
         string(name: 'CollibraInstanceId', defaultValue: '', description: 'ID of the EC2-instance this template should create a proxy for (typically left blank)')
         string(name: 'CollibraListenPort', defaultValue: '443', description: 'Public-facing TCP Port number on which the ELB listens for requests to proxy')
         string(name: 'HaSubnets', description: 'Provide a comma-separated list of user-facing subnet IDs in which to create service-listeners')
         choice(name: 'CertHostingService', choices:[ 'IAM', 'ACM' ], description: 'AWS service containing the certificate to SSL-enable the ELB')
         string(name: 'CollibraListenerCert', description: 'AWS Certificate Manager Certificate ID to bind to SSL listener')
         string(name: 'SecurityGroupIds', description: 'List of security groups to apply to the ELB')
         string(name: 'TargetVPC', description: 'ID of the VPC to deploy cluster nodes into')
         choice(name: 'PublicFacing', choices:[ 'false', 'true' ], description: 'Whether or not proxy is "internet-facing"')
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
                writeFile file: 'ELB.parms.json',
                   text: /
                       [
                           {
                               "ParameterKey": "ProxyForService",
                               "ParameterValue": "${env.ProxyForService}"
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
                               "ParameterKey": "PublicFacing",
                               "ParameterValue": "${env.PublicFacing}"
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

                // Clean up stale AWS resources //
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       if [[ -v ${AWS_CFN_ENDPOINT+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url ${AWS_CFN_ENDPOINT}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       if [[ -v ${R53ZoneId+x} ]]
                       then
                          echo "Attempting to delete any active ${CfnStackRoot}-R53AliasRes-${ProxyForService} stacks..."
                          ${CFNCMD} delete-stack --stack-name ${CfnStackRoot}-R53AliasRes-${ProxyForService} || true
                          sleep 5

                          # Pause if delete is slow
                          while [[ $(
                                      ${CFNCMD} describe-stacks \
                                        --stack-name ${CfnStackRoot}-R53AliasRes-${ProxyForService} \
                                        --query 'Stacks[].{Status:StackStatus}' \
                                        --out text 2> /dev/null | \
                                      grep -q DELETE_IN_PROGRESS
                                     )$? -eq 0 ]]
                          do
                             echo "Waiting for stack ${CfnStackRoot}-R53AliasRes-${ProxyForService} to delete..."
                             sleep 30
                          done
                       fi

                       echo "Attempting to delete any active ${CfnStackRoot}-ElbRes-${ProxyForService} stacks..."
                       ${CFNCMD} delete-stack --stack-name ${CfnStackRoot}-ElbRes-${ProxyForService} || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
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
                       if [[ -v ${AWS_CFN_ENDPOINT+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url ${AWS_CFN_ENDPOINT}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Attempting to create stack ${CfnStackRoot}-ElbRes-${ProxyForService}..."
                       ${CFNCMD} create-stack --stack-name ${CfnStackRoot}-ElbRes-${ProxyForService} \
                           --template-body file://Templates/make_collibra_ELBv2.tmplt.json \
                           --parameters file://ELB.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
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
                               ${CFNCMD} describe-stacks \
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
            when {
                expression {
                    return env.R53ZoneId != '';
                }
            }
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

    post {
        always {
            deleteDir() /* lets be a good citizen */
        }
    }
}
