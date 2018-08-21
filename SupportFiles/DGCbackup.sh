#!/usr/bin/env bash
# shellcheck disable=SC2086
#
# Script to automate the backing-up of the Collibra environment
# Note: MUST be installed on the Collibra console node
#
#################################################################
ADMIN=${ADMIN:-Admin}
PASSWD=${PASSWD:-UNDEF}
S3BUCKET=${S3BUCKET:-UNDEF}
SVCURL="http://127.0.0.1:4402"
DATE=$(date "+%Y%m%d%H%M")

function GetEnvId {
   ENVIRONMENTJSON="$(
         curl -skLu "${ADMIN}":"${PASSWD}" \
           -X GET "${SVCURL}/rest/environment"
      )"

   echo "${ENVIRONMENTJSON}" | jq '.[0].id' | sed 's/"//g'
}

function MkBackup {

   local BKUPJSON
   BKUPJSON="{ \
         \"name\": \"Backup-${DATE}\", \
         \"description\": \"Cron-initiated backup for ${DATE}\", \
         \"database\": \"dgc\", \
         \"dgcBackupOptionSet\": [\"CUSTOMIZATIONS\"], \
         \"repoBackupOptionSet\": [\"DATA\",\"HISTORY\",\"CONFIGURATION\"] \
         } \
      "

   # Intiate backup job
   BKUPJOB=$(
         curl -skLu "${ADMIN}":"${PASSWD}" -X POST "${SVCURL}/rest/backup/${1}" \
           -H 'cache-control: no-cache' -H 'content-type: application/json' \
           -d "${BKUPJSON}"
      )

   BKUPJOBID=$( echo ${BKUPJOB} | jq .id | sed 's/"//g' )

   # Wait for job to finish
   while true
   do
      JOBSTATE=$( curl -skLu "${ADMIN}":"${PASSWD}" \
              -X GET "${SVCURL}/rest/backup/${ENVMTID}/state" \
              -H 'cache-control: no-cache' | \
            jq .POST_PROCESSING.status | sed 's/"//g'
         )
      if [[ ${JOBSTATE} = COMPLETED ]]
      then
         echo "Backup done"
         break
      else
         echo "Job is ${JOBSTATE}: waiting... "
         sleep 10
      fi
   done
}

function FetchBkup {

   # Download the backup file
   printf "Downloading backup-ID %s... " "${BKUPJOBID}"
   curl -skL -u "${ADMIN}":"${PASSWD}" \
     -H "Content-Type:application/x-www-form-urlencoded" \
     -X POST "${SVCURL}/rest/backup/file/${BKUPJOBID}" | \
   aws s3 cp - "s3://${S3BUCKET}/DGC-Backup-${DATE}.zip" && \
   echo "Success" || echo "Failed"

}



##################
## Main Program ##
##################

# Need jq or this stuff blows up...
if [[ $(rpm -q --quiet jq)$? -ne 0 ]]
then
   echo "Missing 'jq' utility. Aborting" > /dev/stderr
   exit 1
fi

# Make sure we've got a password for our backup user
if [[ ${PASSWD} = UNDEF ]]
then
   echo "No backup-admin password passed. Aborting" > /dev/stderr
   exit 1
fi

# Make sure we have a destination
if [[ ${S3BUCKET} = UNDEF ]]
then
   echo "No S3-destination specified. Aborting" > /dev/stderr
   exit 1
fi


ENVMTID=$( GetEnvId )
MkBackup "${ENVMTID}"
FetchBkup
