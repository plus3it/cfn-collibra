pipeline {

    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
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
         string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
         string(name: 'TemplateUrl', description: 'S3-hosted URL for the EC2 template file')
         string(name: 'AdminPubkeyURL', defaultValue: '', description: '(Optional) URL of file containing admin groups SSH public-keys')
         string(name: 'AmiId', description: 'ID of the AMI to launch')
         choice(name: 'AppVolumeDevice', choices:[ 'false', 'true' ], description: 'Whether to attach a secondary volume to host application contents')
         string(name: 'AppVolumeMountPath', defaultValue: '/opt/collibra', description: 'Filesystem path to mount the extra app volume. Ignored if "AppVolumeDevice" is false')
         string(name: 'AppVolumeSize', description: 'Size in GiB of the secondary EBS to create')
         string(name: 'AppVolumeType', defaultValue: 'gp2', description: 'Type of EBS volume to create')
         string(name: 'BackupBucket', description: 'S3 Bucket-name in which to store DGC backups')
         string(name: 'BackupSchedule', defaultValue: '45 0 * * *', description: 'When, in cronie-format, to run backups')
         string(name: 'CloudWatchAgentUrl', defaultValue: 's3://amazoncloudwatch-agent/linux/amd64/latest/AmazonCloudWatchAgent.zip', description: '(Optional) S3 URL to CloudWatch Agent installer')
         string(name: 'InstanceRoleName', description: 'IAM instance role-name to use for signalling')
         string(name: 'InstanceRoleProfile', description: 'IAM instance profile-name to apply to the instance')
         string(name: 'InstanceType', description: 'AWS EC2 instance type to select for launch')
         string(name: 'KeyPairName', description: 'Registered SSH key used to provision the node')
         string(name: 'MuleUri', description: 'A curl-fetchable URL for the Mule ESB software' )
         string(name: 'MuleLicenseUri', description: 'A curl-fetchable URL for the Mule ESB license file')
         choice(name: 'NoReboot', choices:[ 'false', 'true' ], description: 'Whether to prevent the instance from rebooting at completion of build')
         choice(name: 'NoUpdates', choices:[ 'false', 'true' ], description: 'Whether to prevent updating all installed RPMs as part of build process')
         string(name: 'ProvisionUser', defaultValue: 'ec2-user', description: 'Default login-user to create upon instance-launch')
         string(name: 'PypiIndexUrl', defaultValue: 'https://pypi.org/simple', description: 'Source from which to pull Pypi packages')
         string(name: 'RootVolumeSize', defaultValue: '20', description: 'How big to make the root EBS volume (ensure value specified is at least as big as the AMI-default)')
         string(name: 'SecurityGroupIds', description: 'Comma-separated list of EC2 security-groups to apply to the instance')
         string(name: 'SubnetId', description: 'Subnet-ID to deploy EC2 instance into')
         string(name: 'WatchmakerAdminGroups', description: 'What ActiveDirectory groups to give admin access to (if bound to an AD domain)')
         string(name: 'WatchmakerAdminUsers', description: 'What ActiveDirectory users to give admin access to (if bound to an AD domain)')
         string(name: 'WatchmakerComputerName', description: 'Hostname to apply to the deployed instance')
         string(name: 'WatchmakerConfig', description: '(Optional) Path to a Watchmaker config file.  The config file path can be a remote source (i.e. http[s]://, s3://) or local directory (i.e. file://)')
         string(name: 'WatchmakerEnvironment', defaultValue: 'dev', description: 'What build environment to deploy instance to')
         string(name: 'WatchmakerOuPath', description: 'OU-path in which to create Active Directory computer object')
    }

    stages {
        stage ('Cleanup Work Environment') {
            steps {
                deleteDir()
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
                                 "ParameterKey": "BackupSchedule",
                                 "ParameterValue": "${env.BackupSchedule}"
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
                                 "ParameterKey": "MuleLicenseUri",
                                 "ParameterValue": "${env.MuleLicenseUri}"
                             },
                             {
                                 "ParameterKey": "MuleUri",
                                 "ParameterValue": "${env.MuleUri}"
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
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''#!/bin/bash
                       # For compatibility with ancient AWS CLI utilities
                       if [[ -z ${AWS_SVC_ENDPOINT} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url ${AWS_SVC_ENDPOINT}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Attempting to delete any active ${CfnStackRoot}-Ec2Res-ESB stacks..."
                       ${CFNCMD} delete-stack --stack-name ${CfnStackRoot}-Ec2Res-ESB || true
                       sleep 5

                       # Pause if delete is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-Ec2Res-ESB \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q DELETE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-Ec2Res-ESB to delete..."
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
                       if [[ -z ${AWS_SVC_ENDPOINT} ]]
                       then
                          CFNCMD="aws cloudformation --endpoint-url ${AWS_SVC_ENDPOINT}"
                       else
                          CFNCMD="aws cloudformation"
                       fi

                       echo "Attempting to create stack ${CfnStackRoot}-Ec2Res-ESB..."
                       ${CFNCMD} create-stack --stack-name ${CfnStackRoot}-Ec2Res-ESB \
                           --disable-rollback --template-url "${TemplateUrl}" \
                           --parameters file://EC2.parms.json
                       sleep 5

                       # Pause if create is slow
                       while [[ $(
                                   ${CFNCMD} describe-stacks \
                                     --stack-name ${CfnStackRoot}-Ec2Res-ESB \
                                     --query 'Stacks[].{Status:StackStatus}' \
                                     --out text 2> /dev/null | \
                                   grep -q CREATE_IN_PROGRESS
                                  )$? -eq 0 ]]
                       do
                          echo "Waiting for stack ${CfnStackRoot}-Ec2Res-ESB to finish create process..."
                          sleep 30
                       done

                       if [[ $(
                               ${CFNCMD} describe-stacks \
                                 --stack-name ${CfnStackRoot}-Ec2Res-ESB \
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
