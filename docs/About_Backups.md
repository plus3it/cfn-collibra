# Backups Overview

The CloudFormation (CFn) template used to create the EC2s that host the Collibra service elements contains logic for setting up automated backups. When launching the `make_collibra_EC2-standalone.tmplt.json` CFn template, the configuration backup job is governed by way of the following parameters:

* `BackupBucket`: The S3 bucket that will host Collibra backup data. This can be a dedicated bucket or not. Backup-files will be placed in the bucket's `Backups` "folder" (key).
* `BackupSchedule`: When backup jobs should be performed. This value should be specified in `cronie` time-format (i.e., `min hr mo-dy mo wk-dy`).
* `BackupScript`: URL of a `curl`-fetchable copy of the script file. URL must either be anonymously-readable or the URL-string must include authentication information (i.e., `https://name:passwd@F.Q.D.N/...`)
* `BackupUserName`: Name of a user _within the Collibra Console service_ that has at least `ADMIN` level privileges.
* `BackupUserPassword`: The password of the user specified via the `BackupUserName` parameter.

The above parameters are optional &mdash; will be ignored &mdash; if the `CollibraDgcComponent` is not set to `CONSOLE`. If the `CollibraDgcComponent` parameter-value is set to `CONSOLE`, setting values for these parameters becomes mandatory.

## Backup Bucket

A dedicated or shared backup bucket is necessary. There are no restrictions on bucket-naming - even AWS-generated names are acceptable.

All backup-files will be prepended with the `Backups/<INSTANCE_ID>` key. This means that, when using a graphical S3 bucket-viewer, a given instance's backups will appear to be saved to a subfolder of the target bucket's `Backups` folder.

It will be necessary that the instance be granted sufficient access to the target bucket:

* If using the `make_collibra_S3-bucket.tmplt.json` and `make_collibra_IAM-instance.tmplt.json` CFn templates for overall deployment setup, an instance-role will be created to provide the requisite bucket permissions. Ensure this instance-role is attached to the Collibra Console server's EC2 instance.
* If using other IAM instance roles, ensure that the instance role attached to the Collibra Console server's EC2 instance has permissions to the S3 bucket
* If not using IAM instance roles, ensure that the bucket has an appropriate bucket-policy to allow the instance to use the target bucket.

## Backup User

After initial deployment of the Collibra Console, it will be necessary to create a backup user within the Console. This user should have at least `ADMIN` rights. This user should be created with the same name and password passed via the CFn template used to create a Collibra Console deployment-stack.

Note that, prior to creating the backup user, it will be necessary to have:

* Enabled SMTP relaying:
    * If using SES for SMTP relay-service, enable un-sandboxed relay capability within the appropriate region.
    * If using a third-party relay-service, obtain relay credentials and configuration information.
* Set up destination email account for the backup-user's password-reset link emails to be sent to.
* Successfully completed the `1.1 Mail configuration` console configuration task.

Until the backup user is fully-created, the automated backup jobs will remain non-functional.
