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
        string(name: 'NotifyEmail', description: 'Email address to send job-status notifications to')
        string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
        string(name: 'AwsSvcDomain',  description: 'Override the service-endpoint DNS-domain as necessary')
        string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
        string(name: 'JobRoot', description: 'Unique token to prepend to all stack-element names')
        string(name: 'ConsoleCert', description: 'S3 URL to Collibra Console SSL certificate')
        string(name: 'ConsolePrivateKey', description: 'S3 URL to Collibra Console private key file')
        string(name: 'ConsoleTrustChain', description: 'S3 URL to certificate trust-chain file')
        string(name: 'DgcCert', description: 'S3 URL to Collibra DGC SSL certificate')
        string(name: 'DgcPrivateKey', description: 'S3 URL to Collibra DGC private key file')
        string(name: 'DgcTrustChain', description: 'S3 URL to certificate trust-chain file')
    }

    stages {
        stage ('Amend Job-Env') {
            steps {
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
                    // Export credentials to rest of stages
                    script {
                        env.AWS_ACCESS_KEY_ID = AWS_ACCESS_KEY_ID
                        env.AWS_SECRET_ACCESS_KEY = AWS_SECRET_ACCESS_KEY
                    }

                    // Set endpoint-override vars as necessary
                    script {
                        if ( env.AwsSvcDomain == '' ) {
                            env.IAMCMD = "aws iam"
                        } else {
                            env.IAMCMD = "aws iam --endpoint-url https://iam.${AWS_SVC_DOMAIN}/ "
                        }
                    }
                }
            }
        }
        stage ('Delete Old Certificates') {
            parallel {
                stage ('Console') {
                    steps {
                        sh '''#!/bin/bash
                            CERTNAME="${JobRoot}-Console-Cert"
                            printf "Looking for existing certificate [%s]... " "${CERTNAME}"
                            CERTEXISTS=$( ${IAMCMD} get-server-certificate \
                                            --server-certificate-name "${CERTNAME}" \
                                            > /dev/null 2>&1 )$?

                            if [[ ${CERTEXISTS} -eq 0 ]]
                            then
                                echo "Found"
                                printf "Nuking certificate [%s]... " "${CERTNAME}"
                                ${IAMCMD} delete-server-certificate --server-certificate-name \
                                  "${CERTNAME}" > /dev/null 2>&1 && echo Success || \
                                  ( echo "FAILED" ; exit 1 )
                            else
                                echo "No such certificate exists"
                            fi
                        '''
                    }
                }
                stage ('DGC') {
                    steps {
                        sh '''#!/bin/bash
                            CERTNAME="${JobRoot}-DGC-Cert"
                            printf "Looking for existing certificate [%s]... " "${CERTNAME}"
                            CERTEXISTS=$( ${IAMCMD} get-server-certificate \
                                            --server-certificate-name "${CERTNAME}" \
                                            > /dev/null 2>&1 )$?

                            if [[ ${CERTEXISTS} -eq 0 ]]
                            then
                                echo "Found"
                                printf "Nuking certificate [%s]... " "${CERTNAME}"
                                ${IAMCMD} delete-server-certificate --server-certificate-name \
                                  "${CERTNAME}" > /dev/null 2>&1 && echo Success || \
                                  ( echo "FAILED" ; exit 1 )
                            else
                                echo "No such certificate exists"
                            fi
                        '''
                    }
                }
            }
        }
        stage ('Upload New Certificates') {
            parallel {
                stage ('Console') {
                    steps {
                        sh '''#!/bin/bash
                            CERTNAME="${JobRoot}-Console-Cert"
                            printf "Uploading certificate [%s]... " "${CERTNAME}"
                            ${IAMCMD} upload-server-certificate --server-certificate-name "${CERTNAME}" \
                              --certificate-chain "${ConsoleTrustChain}" \
                              --certificate-body "${ConsoleCert}" \
                              --private-key "${ConsolePrivateKey}" > /dev/null 2>&1 \
                                && echo "Success" || ( echo "FAILED" ; exit 1 )
                        '''
                    }
                }
                stage ('DGC') {
                    steps {
                        sh '''#!/bin/bash
                            CERTNAME="${JobRoot}-DGC-Cert"
                            printf "Uploading certificate [%s]... " "${CERTNAME}"
                            ${IAMCMD} upload-server-certificate --server-certificate-name "${CERTNAME}" \
                              --certificate-chain "${DgcTrustChain}" \
                              --certificate-body "${ConsoleCert}" \
                              --private-key "${ConsolePrivateKey}" > /dev/null 2>&1 \
                                && echo "Success" || ( echo "FAILED" ; exit 1 )
                        '''
                    }
                }
            }
        }
        stage ('Verify Certificates') {
            parallel {
                stage ('Console') {
                    steps {
                        sh '''#!/bin/bash
                            CERTNAME="${JobRoot}-Console-Cert"
                            echo "Verifying [${CERTNAME}] upload..."
                            CERTRET=$( ${IAMCMD} get-server-certificate \
                                          --server-certificate-name "${CERTNAME}" \
                                          --query 'ServerCertificate.ServerCertificateMetadata' \
                                          2> /dev/null )

                            if [[ -z ${CERTRET} ]]
                            then
                                echo "Failed to upload certificate [${CERTNAME}]"
                                exit 1
                            else
                                echo "${CERTRET}"
                            fi
                        '''
                    }
                }
                stage ('DGC') {
                    steps {
                        sh '''#!/bin/bash
                            CERTNAME="${JobRoot}-DGC-Cert"
                            echo "Verifying [${CERTNAME}] upload..."
                            CERTRET=$( ${IAMCMD} get-server-certificate \
                                          --server-certificate-name "${CERTNAME}" \
                                          --query 'ServerCertificate.ServerCertificateMetadata' \
                                          2> /dev/null )

                            if [[ -z ${CERTRET} ]]
                            then
                                echo "Failed to upload certificate [${CERTNAME}]"
                                exit 1
                            else
                                echo "${CERTRET}"
                            fi
                        '''
                    }
                }
            }
        }
    }

    // Do after job-stages end
    post {
        // Clean up work-dir no matter what
        always {
            deleteDir()
        }
        // Emit a failure-email if a notification-address is set
        failure {
            script {
                if ( env.NotifyEmail != '' ) {
                    mail to: "${env.NotifyEmail}",
                        subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
                        body: "Something is wrong with ${env.BUILD_URL}"
                }
            }
        }
    }
}
