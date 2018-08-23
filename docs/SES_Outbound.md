# Enable SES-Based SMTP Relaying

To use SES for outbound email sevice, one will typically wish to accomplish three tasks: verifying a domain so that it is send-enabled; create one or more SMTP IAM credentials that can be used by SMTP clients to authenticate to the SES relay-endpoints; un-sandbox SES. Once these steps are done, configure your application to send mail.

## Verify New Domain

1. Enter SES web console
1. Select `Domains` under the `Identity Management` heading
1. Click on the `Verify a New Domain` button
1. In the `Verify a New Domain` pop-up, enter the name of the domain to verify in the `Domain` box, then click the `Verify This Domain` button (`Generate DKIM Settings` box may be left as-is).
1. In the next `Verify a New Domain` pop-up, click on the `Use Route53` button.
1. In the `Use Route53` pop-up:
    1. Ensure the `Domain Verification Record` box is checked
    1. Ensure the `Email Receiving Record` box is checked
    1. Click on the `Create Record Sets` button

At this point, the pop-ups close and you are returned to the `Verify a New Domain` page. The newly-added domain will likely be in a `pending verification` state. Wait 30-90 seconds and then refresh page: the `Verification Status` should now show as `verified` (repeat the refresh as necessary till the state changes).

## Create SMTP IAM Credentials

1. Enter SES web console.
1. Click on the `SMTP Settings` link under the `Email Sending` heading.
1. Click on the `Create My SMTP Credentials` button. This will open the `Create User` section of the AWS IAM console.
1. Enter a descriptive, yet conformant,  name into the `IAM User Name` box. Then click on the `Create` button.
1. On the next page, either click on the `Download Credentials` button or the `Show User SMTP Security Credentials` link.
1. Save the resulting displayed/downloaded credentials to someplace persistent and secure.
1. Click on the `Close` button.

## Un-sandbox SES.

1. Enter SES web console.
1. Click on the `Sending Statistics` link under the `Email Sending` heading
1. If your SES is sandboxed, a blue warning box will appear at the top of the page.
1. Click the `Request a Sending Limit Increase` button to get sandboxing disabled.

To properly fill out the request, read and follow the [AWS documentation](https://docs.aws.amazon.com/ses/latest/DeveloperGuide/request-production-access.html?icmpid=docs_ses_console). Removal from the sandbox typically takes one business day to effect.


## Configure Application SMTP Settings

Once the prior tasks are done, SES is basically ready to act as an SMTP relay. To configure your application, you will need the previously-created IAM-user credentials and the SES endpoint-settings. The SES endpoint settings may be found by:

1. Enter SES web console.
1. Click on the `SMTP Settings` link under the `Email Sending` heading.
1. Note the values for `Server Name` and `port`: generally, port 587 is preferred.
1. Fill out your application's SMTP settings with the previously gathered values for `Server Name`, `Port` and recently-generated IAM SMTP-user credentials
