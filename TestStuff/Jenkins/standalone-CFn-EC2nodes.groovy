
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
         string(name: 'AmiId', description: '')
         string(name: 'AppVolumeDevice', description: '')
         string(name: 'AppVolumeMountPath', description: '')
         string(name: 'AppVolumeSize', description: '')
         string(name: 'AppVolumeType', description: '')
         string(name: 'CfnBootstrapUtilsUrl', description: '')
         string(name: 'CfnGetPipUrl', description: '')
         string(name: 'CloudWatchAgentUrl', description: '')
         string(name: 'CollibraConsolePassword', description: '')
         string(name: 'CollibraDataDir', description: '')
         string(name: 'CollibraDgcComponent', description: '')
         string(name: 'CollibraInstallerUrl', description: '')
         string(name: 'CollibraRepoPassword', description: '')
         string(name: 'CollibraSoftwareDir', description: '')
         string(name: 'InstanceRoleName', description: '')
         string(name: 'InstanceRoleProfile', description: '')
         string(name: 'InstanceType', description: '')
         string(name: 'KeyPairName', description: '')
         string(name: 'NoPublicIp', description: '')
         string(name: 'NoReboot', description: '')
         string(name: 'NoUpdates', description: '')
         string(name: 'PrivateIp', description: '')
         string(name: 'PypiIndexUrl', description: '')
         string(name: 'RootVolumeSize', description: '')
         string(name: 'SecurityGroupIds', description: '')
         string(name: 'SubnetId', description: '')
         string(name: 'ToggleCfnInitUpdate', description: '')
         string(name: 'WatchmakerAdminGroups', description: '')
         string(name: 'WatchmakerAdminUsers', description: '')
         string(name: 'WatchmakerComputerName', description: '')
         string(name: 'WatchmakerConfig', description: '')
         string(name: 'WatchmakerEnvironment', description: '')
         string(name: 'WatchmakerOuPath', description: '')
    }

    stages {
        stage ('Cleanup Work Environment') {
            steps {
                deleteDir()
                git branch: "${GitProjBranch}",
                    credentialsId: "${GitCred}",
                    url: "${GitProjUrl}"
                writeFile file: 'EC2.parms.json',
                   text: /
                         [
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
                                 "ParameterKey": "CfnBootstrapUtilsUrl",
                                 "ParameterValue": "${env.CfnBootstrapUtilsUrl}"
                             },
                             {
                                 "ParameterKey": "CfnGetPipUrl",
                                 "ParameterValue": "${env.CfnGetPipUrl}"
                             },
                             {
                                 "ParameterKey": "CloudWatchAgentUrl",
                                 "ParameterValue": "${env.CloudWatchAgentUrl}"
                             },
                             {
                                 "ParameterKey": "CollibraConsolePassword",
                                 "ParameterValue": "${env.CollibraConsolePassword}"
                             },
                             {
                                 "ParameterKey": "CollibraDataDir",
                                 "ParameterValue": "${env.CollibraDataDir}"
                             },
                             {
                                 "ParameterKey": "CollibraDgcComponent",
                                 "ParameterValue": "${env.CollibraDgcComponent}"
                             },
                             {
                                 "ParameterKey": "CollibraInstallerUrl",
                                 "ParameterValue": "${env.CollibraInstallerUrl}"
                             },
                             {
                                 "ParameterKey": "CollibraRepoPassword",
                                 "ParameterValue": "${env.CollibraRepoPassword}"
                             },
                             {
                                 "ParameterKey": "CollibraSoftwareDir",
                                 "ParameterValue": "${env.CollibraSoftwareDir}"
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
                                 "ParameterKey": "NoPublicIp",
                                 "ParameterValue": "${env.NoPublicIp}"
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
                                 "ParameterKey": "PrivateIp",
                                 "ParameterValue": "${env.PrivateIp}"
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
                                 "ParameterKey": "ToggleCfnInitUpdate",
                                 "ParameterValue": "${env.ToggleCfnInitUpdate}"
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
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       echo "Attempting to delete any active ${CfnStackRoot}-Ec2-${CollibraDgcComponent} stacks..."
                       aws cloudformation delete-stack --stack-name ${CfnStackRoot}-Ec2-${CollibraDgcComponent} || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
                                     --stack-name ${CfnStackRoot}-Ec2-${CollibraDgcComponent} \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-Ec2-${CollibraDgcComponent} to delete..."
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
                       echo "Attempting to create stack ${CfnStackRoot}-Ec2-${CollibraDgcComponent}..."
                       aws cloudformation create-stack --stack-name ${CfnStackRoot}-Ec2-${CollibraDgcComponent} \
                           --template-body file://Templates/make_collibra_EC2-standalone.tmplt.json \
                           --parameters file://EC2.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   aws cloudformation describe-stacks \
                                     --stack-name ${CfnStackRoot}-Ec2-${CollibraDgcComponent} \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-Ec2-${CollibraDgcComponent} to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               aws cloudformation describe-stacks \
                                 --stack-name ${CfnStackRoot}-Ec2-${CollibraDgcComponent} \
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
