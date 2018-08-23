# Configuring Inbound SMTP Within [SES](https://aws.amazon.com/ses/)

If leveraging SES for Collibra-related service accounts (`no-reply`, administrator, backup-admin and similar accounts), it will be necessary to set up a few AWS resources to facilitate SES's inboun email capabilities. This document assumes that service accounts' emails will be hosted in an S3 bucket rather than a "real" email account.

## S3 Tasks

It will be necessary to have an S3 bucket available to act as a delivery destination. While SES-used buckets do not have to be exclusive to SES's use, this document assumes the use of a dedicated bucket.

### S3 Bucket Creation

Create a generic, private S3 bucket. This can be done via the AWS web console, the AWS CLI or via CFn template (not currently included in this project). If automated age-off of emails is desired, ensure that the bucket has an appropriate lifecycle policy attached.

### S3 Bucket Security

In order for SES to deliver emails to a bucket destination, it will be necessary to give requisite permissions to the bucket to the SES service. In the AWS S3 web-console:

1. After bucket-creation, click on the target bucket's name.
1. Click on the `Permissions` tab.
1. Click on the `Bucket Policy` button.
1. Place an appropriate policy-description into the `Bucket policy editor` window. This policy should look something like:

    ```json
    {
        "Statement": [
            {
                "Action": "s3:PutObject",
                "Condition": {
                    "StringEquals": {
                        "aws:Referer": "${AWS_ACCOUNT_NUMBER}"
                    }
                },
                "Effect": "Allow",
                "Principal": {
                    "Service": "ses.amazonaws.com"
                },
                "Resource": "arn:aws:s3:::${S3_BUCKET_NAME}/*",
                "Sid": "AllowSESPuts"
            }
        ],
        "Version": "2012-10-17"
    }
    ```
1. Hit the `Save` button to apply the policy-settings to the bucket

## SES Tasks

This section assumes that SES has already been cofnigured with an appropriate verified domain. This section covers setting up inbound email rule-sets and verifying an SES-managed recipient.

### Rule Sets

Under the AWS SES web console:

1. Click on the `Rule Sets` link in the left nav-bar (under the `Email Receiving` heading)
1. Click on the `Create a New Rule Set` button
1. In the first pop-up, supply a name for the new rule, then click on the `Create a Rule Set` button
1. Click on the name of the new rule-set to begin adding rules to the rule-set. Doing so will open a new page.
1. Click on the `Create Rule` button. This will open a page where you are asked to add one or more email recipient addresses. Add the email address you would like to create, then click on the `Add Recipient` button. When all recipients have been added (we're assuming only one recipient _in_ the verified SES domain), click on the `Next Step` button.
1. On the `Actions` page, select `S3` from the `Select n action type` drop-down:
    1. Select the previously configured S3 bucket from the `S3 bucket` drop-down.
    1. Place a value of `SMTP/<USER>@<FQDN> in the `Object key prefix` field. This sets where the user's SES-received emails will be placed within the previously-selected S3 bucket.
    1. Leave the other two options unset.
    1. Click on the `Next Step` button

1. On the `Rule Details` page:
    1. Enter a conformant name into the `Rule name` box. 
    1. Ensure that the `Enabled` box is checked.
    1. Ensure that the `Enable spam and virus scanning` box is checked.
    1. Leave all other settings unmodified.
    1. Click on the `Next Step` button.
1. On the `Review` page, make sure everything looks good, then click on the `Create Rule` button.

The SES-managed email address should now be ready to receive emails and save them to S3.

### Verify address

Under the AWS SES web console:

1. Click on the `Email Addresses` link.
1. Click on the `Verify a New Email Address` button.
1. In the `Verify a New Email Address` pop-up, enter the email address created in the previous `Rule Sets` section.

The console should show the new address with a `pending verification` step. To verify:

1. Switch to the AWS S3 web console
1. Navigate to the bucket/folder configured in the previous `Rule Sets` section.
1. Download the most recently-created file (this should _not_ be the one named `AMAZON_SES_SETUP_NOTIFICATION`)
1. Open the file with a basic text editor (like `Notepad++`)
1. Locate the email-verification link within the file and copy it.
1. Paste the copied link into your browser and hit <ENTER>
1. A "Congratulations" message should appear in your browser. Close the window.
1. Return to the AWS SES web console
1. Click on the `Email Addresses` link.
1. The new address should now show as `verified`

## Closing

This verified address should now be usable within the Collibra Console. Any messages generated for this address will now show up in the previously browsed-to S3 bucket. Use the previous download/edit method to view new such mails.
