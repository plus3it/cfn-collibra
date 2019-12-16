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
         string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
         string(name: 'AwsSvcDomain',  description: 'Override the service-endpoint DNS domain FQDN as necessary')
         string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
         string(name: 'ParmFileS3location', description: 'S3 URL for parameter file (e.g., "s3://<bucket>/<object_key>")')
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
         string(name: 'SecurityGroupIds', description: 'List of security groups to apply to the EC2')
         string(name: 'InstanceRoleProfile', description: 'IAM instance profile-name to apply to the EC2')
         string(name: 'InstanceRoleName', description: 'IAM instance role-name to use for signalling')
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

                    def TemplateUrl = sh script:'awk -F "=" \'/TemplateUrl/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.TemplateUrl = TemplateUrl.trim()

                    def AdminPubkeyURL = sh script:'awk -F "=" \'/AdminPubkeyURL/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.AdminPubkeyURL = AdminPubkeyURL.trim()

                    def AmiId = sh script:'awk -F "=" \'/AmiId/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.AmiId = AmiId.trim()

                    def AppVolumeDevice = sh script:'awk -F "=" \'/AppVolumeDevice/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.AppVolumeDevice = AppVolumeDevice.trim()

                    def AppVolumeMountPath = sh script:'awk -F "=" \'/AppVolumeMountPath/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.AppVolumeMountPath = AppVolumeMountPath.trim()

                    def AppVolumeSize = sh script:'awk -F "=" \'/AppVolumeSize/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.AppVolumeSize = AppVolumeSize.trim()

                    def AppVolumeType = sh script:'awk -F "=" \'/AppVolumeType/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.AppVolumeType = AppVolumeType.trim()

                    def BackupBucket = sh script:'awk -F "=" \'/BackupBucket/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.BackupBucket = BackupBucket.trim()

                    def CloudWatchAgentUrl = sh script:'awk -F "=" \'/CloudWatchAgentUrl/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.CloudWatchAgentUrl = CloudWatchAgentUrl.trim()

                    def InstanceType = sh script:'awk -F "=" \'/InstanceType/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.InstanceType = InstanceType.trim()

                    def KeyPairName = sh script:'awk -F "=" \'/KeyPairName/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.KeyPairName = KeyPairName.trim()

                    def MuleMmcWarUri = sh script:'awk -F "=" \'/MuleMmcWarUri/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.MuleMmcWarUri = MuleMmcWarUri.trim()

                    def NoReboot = sh script:'awk -F "=" \'/NoReboot/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.NoReboot = NoReboot.trim()

                    def NoUpdates = sh script:'awk -F "=" \'/NoUpdates/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.NoUpdates = NoUpdates.trim()

                    def ProvisionUser = sh script:'awk -F "=" \'/ProvisionUser/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.ProvisionUser = ProvisionUser.trim()

                    def PypiIndexUrl = sh script:'awk -F "=" \'/PypiIndexUrl/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.PypiIndexUrl = PypiIndexUrl.trim()

                    def RootVolumeSize = sh script:'awk -F "=" \'/RootVolumeSize/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.RootVolumeSize = RootVolumeSize.trim()

                    def SubnetId = sh script:'awk -F "=" \'/SubnetId/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.SubnetId = SubnetId.trim()

                    def WatchmakerAdminGroups = sh script:'awk -F "=" \'/WatchmakerAdminGroups/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.WatchmakerAdminGroups = WatchmakerAdminGroups.trim()

                    def WatchmakerAdminUsers = sh script:'awk -F "=" \'/WatchmakerAdminUsers/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.WatchmakerAdminUsers = WatchmakerAdminUsers.trim()

                    def WatchmakerComputerName = sh script:'awk -F "=" \'/WatchmakerComputerName/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.WatchmakerComputerName = WatchmakerComputerName.trim()

                    def WatchmakerConfig = sh script:'awk -F "=" \'/WatchmakerConfig/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.WatchmakerConfig = WatchmakerConfig.trim()

                    def WatchmakerEnvironment = sh script:'awk -F "=" \'/WatchmakerEnvironment/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.WatchmakerEnvironment = WatchmakerEnvironment.trim()

                    def WatchmakerOuPath = sh script:'awk -F "=" \'/WatchmakerOuPath/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.WatchmakerOuPath = WatchmakerOuPath.trim()

                    def R53ZoneId = sh script:'awk -F "=" \'/R53ZoneId/{ print $2 }\' Pipeline.envs',
                        returnStdout: true
                    env.R53ZoneId = R53ZoneId.trim()
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
                writeFile file: 'EC2.parms.json',
                   text: /
                         [
                             {
                                 "ParameterKey": "AdminPubkeyURL",
                                 "ParameterValue": "${env.AdminPubkeyURL}"
                             },
                             {
                                 "ParameterKey": "AmiId",
                                 "ParameterValue": "${env.AmiId}"
                             },
                             {
                                 "ParameterKey": "AppVolumeDevice",
                                 "ParameterValue": "${env.AppVolumeDevice}"
                             },
                             {
                                 "ParameterKey": "AppVolumeMountPath",
                                 "ParameterValue": "${env.AppVolumeMountPath}"
                             },
                             {
                                 "ParameterKey": "AppVolumeSize",
                                 "ParameterValue": "${env.AppVolumeSize}"
                             },
                             {
                                 "ParameterKey": "AppVolumeType",
                                 "ParameterValue": "${env.AppVolumeType}"
                             },
                             {
                                 "ParameterKey": "BackupBucket",
                                 "ParameterValue": "${env.BackupBucket}"
                             },
                             {
                                 "ParameterKey": "CloudWatchAgentUrl",
                                 "ParameterValue": "${env.CloudWatchAgentUrl}"
                             },
                             {
                                 "ParameterKey": "InstanceRoleName",
                                 "ParameterValue": "${env.InstanceRoleName}"
                             },
                             {
                                 "ParameterKey": "InstanceRoleProfile",
                                 "ParameterValue": "${env.InstanceRoleProfile}"
                             },
                             {
                                 "ParameterKey": "InstanceType",
                                 "ParameterValue": "${env.InstanceType}"
                             },
                             {
                                 "ParameterKey": "KeyPairName",
                                 "ParameterValue": "${env.KeyPairName}"
                             },
                             {
                                 "ParameterKey": "MuleMmcWarUri",
                                 "ParameterValue": "${env.MuleMmcWarUri}"
                             },
                             {
                                 "ParameterKey": "NoReboot",
                                 "ParameterValue": "${env.NoReboot}"
                             },
                             {
                                 "ParameterKey": "NoUpdates",
                                 "ParameterValue": "${env.NoUpdates}"
                             },
                             {
                                 "ParameterKey": "ProvisionUser",
                                 "ParameterValue": "${env.ProvisionUser}"
                             },
                             {
                                 "ParameterKey": "PypiIndexUrl",
                                 "ParameterValue": "${env.PypiIndexUrl}"
                             },
                             {
                                 "ParameterKey": "RootVolumeSize",
                                 "ParameterValue": "${env.RootVolumeSize}"
                             },
                             {
                                 "ParameterKey": "SecurityGroupIds",
                                 "ParameterValue": "${env.SecurityGroupIds}"
                             },
                             {
                                 "ParameterKey": "SubnetId",
                                 "ParameterValue": "${env.SubnetId}"
                             },
                             {
                                 "ParameterKey": "WatchmakerAdminGroups",
                                 "ParameterValue": "${env.WatchmakerAdminGroups}"
                             },
                             {
                                 "ParameterKey": "WatchmakerAdminUsers",
                                 "ParameterValue": "${env.WatchmakerAdminUsers}"
                             },
                             {
                                 "ParameterKey": "WatchmakerComputerName",
                                 "ParameterValue": "${env.WatchmakerComputerName}"
                             },
                             {
                                 "ParameterKey": "WatchmakerConfig",
                                 "ParameterValue": "${env.WatchmakerConfig}"
                             },
                             {
                                 "ParameterKey": "WatchmakerEnvironment",
                                 "ParameterValue": "${env.WatchmakerEnvironment}"
                             },
                             {
                                 "ParameterKey": "WatchmakerOuPath",
                                 "ParameterValue": "${env.WatchmakerOuPath}"
                             }
                         ]
                   /
            }
        }

        stage ('Nuke Stale R53 Resources') {
            when {
                expression {
                    return env.R53ZoneId != '';
                }
            }
            steps {
                // Clean up stale AWS resources //
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
                       # For compatibility with ancient AWS CLI utilities
                       if [[ -v ${AWS_SVC_DOMAIN+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url cloudformation.${AWS_SVC_DOMAIN}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Attempting to delete any active ${CfnStackRoot}-R53Res-MMC stacks..."
                       ${CFNCMD} delete-stack --stack-name ${CfnStackRoot}-R53Res-MMC || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-R53Res-MMC \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-R53Res-MMC to delete..."
                          sleep 30
                       done
                    '''
                }
            }
        }

        stage ('Nuke Stale EC2 Resources') {
            steps {
                // Clean up stale AWS resources //
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
                       # For compatibility with ancient AWS CLI utilities
                       if [[ -v ${AWS_SVC_DOMAIN+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url cloudformation.${AWS_SVC_DOMAIN}"
                       else
                          CFNCMD="aws cloudformation"
                       fi
                       echo "Attempting to delete any active ${CfnStackRoot}-Ec2Res-MMC stacks..."
                       ${CFNCMD} delete-stack --stack-name ${CfnStackRoot}-Ec2Res-MMC || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-Ec2Res-MMC \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-Ec2Res-MMC to delete..."
                          sleep 30
                       done
                    '''
                }
            }
        }
        stage ('Launch EC2 Template') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       # For compatibility with ancient AWS CLI utilities
                       if [[ -v ${AWS_SVC_DOMAIN+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url cloudformation.${AWS_SVC_DOMAIN}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Attempting to create stack ${CfnStackRoot}-Ec2Res-MMC..."
                       ${CFNCMD} create-stack --stack-name ${CfnStackRoot}-Ec2Res-MMC \
                           --disable-rollback --template-url "${TemplateUrl}" \
                           --parameters file://EC2.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-Ec2Res-MMC \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-Ec2Res-MMC to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               ${CFNCMD} describe-stacks \
                                 --stack-name ${CfnStackRoot}-Ec2Res-MMC \
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
                                 "ParameterKey": "DependsOnStack",
                                 "ParameterValue": "${CfnStackRoot}-Ec2Res-MMC"
                             },
                             {
                                 "ParameterKey": "PrivateR53Fqdn",
                                 "ParameterValue": "${env.WatchmakerComputerName}"
                             },
                             {
                                 "ParameterKey": "PrivateR53ZoneId",
                                 "ParameterValue": "${env.R53ZoneId}"
                             },
                             {
                                 "ParameterKey": "ZoneTtl",
                                 "ParameterValue": "60"
                             }
                         ]
                   /
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       # For compatibility with ancient AWS CLI utilities
                       if [[ -v ${AWS_SVC_DOMAIN+x} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url cloudformation.${AWS_SVC_DOMAIN}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Bind a R53 Alias to the ELB"
                       ${CFNCMD} create-stack --stack-name ${CfnStackRoot}-R53Res-MMC \
                           --template-body file://Templates/make_collibra_R53-record.tmplt.json \
                           --parameters file://R53alias.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-R53Res-MMC \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-R53Res-MMC to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               ${CFNCMD} describe-stacks \
                                 --stack-name ${CfnStackRoot}-R53Res-MMC \
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
