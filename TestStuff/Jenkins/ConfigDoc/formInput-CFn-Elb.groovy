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
        AWS_CFN_ENDPOINT = "${AwsCfnEndpoint}"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
         string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
         string(name: 'AwsCfnEndpoint',  description: 'Override the CFN-endpoint as necessary')
         string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
         string(name: 'SecurityGroupIds', description: 'List of security groups to apply to the ELB')
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

                    def ProxyForService = sh script:'awk -F "=" \'/ProxyForService/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.ProxyForService = ProxyForService.trim()

                    def UserProxyFqdn = sh script:'awk -F "=" \'/UserProxyFqdn/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.UserProxyFqdn = UserProxyFqdn.trim()

                    def R53ZoneId = sh script:'awk -F "=" \'/R53ZoneId/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.R53ZoneId = R53ZoneId.trim()

                    def ElbShortName = sh script:'awk -F "=" \'/ElbShortName/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.ElbShortName = ElbShortName.trim()

                    def CollibraInstanceId = sh script:'awk -F "=" \'/CollibraInstanceId/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.CollibraInstanceId = CollibraInstanceId.trim()

                    def CollibraListenPort = sh script:'awk -F "=" \'/CollibraListenPort/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.CollibraListenPort = CollibraListenPort.trim()

                    def HaSubnets = sh script:'awk -F "=" \'/HaSubnets/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.HaSubnets = HaSubnets.trim()

                    def CertHostingService = sh script:'awk -F "=" \'/CertHostingService/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.CertHostingService = CertHostingService.trim()

                    def CollibraListenerCert = sh script:'awk -F "=" \'/CollibraListenerCert/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.CollibraListenerCert = CollibraListenerCert.trim()

                    def TargetVPC = sh script:'awk -F "=" \'/TargetVPC/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.TargetVPC = TargetVPC.trim()

                    def PublicFacing = sh script:'awk -F "=" \'/PublicFacing/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.PublicFacing = PublicFacing.trim()
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
                       # Bail on failures
                       set -euo pipefail

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
                       # Bail on failures
                       set -euo pipefail

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
                       # Bail on failures
                       set -euo pipefail

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
