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
        AWS_DEFAULT_REGION = "us-east-1"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
         string(name: 'NotifyEmail', description: 'Email address to send job-status notifications to')
         string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
         string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
         string(name: 'RootName', description: 'Substring to match against EC2 tag:Name value')
         string(name: 'TagName', description: 'Name of the tag to apply')
         string(name: 'TagValue', description: 'Value of the tag to apply')
    }

    stages {
        stage ('Tag Volumes') {
            steps {
                // Make sure work-directory is clean //
                deleteDir()

                // Pull AWS credentials from Jenkins credential-store
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
                    // Pull parameter-file to work-directory
                    sh '''#!/bin/bash
                        # Get list of volumes to tag
                        EBSES2TAG="$( aws ec2 describe-instances \
                           --query 'Reservations[].Instances[].BlockDeviceMappings[].Ebs.VolumeId' \
                           --filters 'Name=tag:Name,Values=*'${RootName}'*' \
                           --output text | tr '\t' ' '
                        )"

                        # Announce what we're doing
                        echo "Applying tags to: ${EBSES2TAG}"

                        # Tag-em
                        aws ec2 create-tags --resources ${EBSES2TAG} \
                          --tags "Key=${TagName},Value=${TagValue}"
                    '''
                }
            }
        }
    }
}
