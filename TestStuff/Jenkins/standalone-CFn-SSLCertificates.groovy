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
         string(name: 'AwsSvcEndpoint',  description: 'Override the CFN-endpoint as necessary')
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
        stage ('Collibra Console') {
            parallel {
                stage ('Delete Old Console Certificates') {
                    steps {
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh '''#!/bin/bash
                               if [[ -z ${AWS_SVC_ENDPOINT} ]]
                               then
                                  export AWSCMD="aws iam"
                               else
                                  export AWSCMD="aws iam --endpoint-url ${AWS_SVC_ENDPOINT}"
                               fi
        
                               CERTNAME="${JobRoot}-Console-Cert"
                               printf "Looking for existing certificate [%s]... " "${CERTNAME}"
                               CERTEXISTS=$( ${AWSCMD} get-server-certificate \
                                               --server-certificate-name "${CERTNAME}" \
                                               > /dev/null 2>&1 )$?
        
                               if [[ ${CERTEXISTS} -eq 0 ]]
                               then
                                  echo "Found"
                                  printf "Nuking certificate [%s]... " "${CERTNAME}"
                                  ${AWSCMD} delete-server-certificate --server-certificate-name \
                                    "${CERTNAME}" > /dev/null 2>&1 && echo Success || \
                                    ( echo "FAILED" ; exit 1 )
                               else
                                  echo "No such certificate exists"
                               fi
                            '''
                        }
                    }
                }
                stage ('Upload Console Certificates') {
                    steps {
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh '''#!/bin/bash
                               if [[ -z ${AWS_SVC_ENDPOINT} ]]
                               then
                                  export AWSCMD="aws iam"
                               else
                                  export AWSCMD="aws iam --endpoint-url ${AWS_SVC_ENDPOINT}"
                               fi

                               printf "Uploading certificate [%s]... " "\${CERTNAME}"
                               ${AWSCMD} upload-server-certificate --server-certificate-name "\${CERTNAME}" \
                                 --certificate-body ${ConsoleCert} \
                                 --private-key "${ConsolePrivateKey}" > /dev/null 2>&1 \
                                   && echo "Success" || ( echo "FAILED" ; exit 1 )
                               ${AWSCMD} 
                            '''
                        }
                    }
                }
            }
        }
        stage ('Collibra DGC') {
            parallel {
                stage ('Delete Old DGC Certificates') {
                    steps {
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh '''#!/bin/bash
                               if [[ -z ${AWS_SVC_ENDPOINT} ]]
                               then
                                  export AWSCMD="aws iam"
                               else
                                  export AWSCMD="aws iam --endpoint-url ${AWS_SVC_ENDPOINT}"
                               fi
        
                               CERTNAME="${JobRoot}-DGC-Cert"
                               printf "Looking for existing certificate [%s]... " "${CERTNAME}"
                               CERTEXISTS=$( ${AWSCMD} get-server-certificate \
                                               --server-certificate-name "${CERTNAME}" \
                                               > /dev/null 2>&1 )$?
        
                               if [[ ${CERTEXISTS} -eq 0 ]]
                               then
                                  echo "Found"
                                  printf "Nuking certificate [%s]... " "${CERTNAME}"
                                  ${AWSCMD} delete-server-certificate --server-certificate-name \
                                    "${CERTNAME}" > /dev/null 2>&1 && echo Success || \
                                    ( echo "FAILED" ; exit 1 )
                               else
                                  echo "No such certificate exists"
                               fi
                            '''
                        }
                    }
                }
                stage ('Upload DGC Certificates') {
                    steps {
                        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh '''#!/bin/bash
                               echo "NO-OP"
                            '''
                        }
                    }
                }
            }
        }
    }
}
