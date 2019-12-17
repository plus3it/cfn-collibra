# README

This directory contains Jenkins pipeline definition files. In order for these files to operate correctly, the Jenkins agent-nodes must:

* Have the AWS CLI installed and usable &ndash; preferably a fairly up-to-date version
* Have the "CloudBees Amazon Web Services Credentials Plugin" installed

## CloudFormation Templates

Most of the pipeline-definitions will pull CFn templates out of a git repository using git-over-ssh. To do so, it will be necessary to have created a deploy-key in the target git repository and stored the deploy-key's SSH private-key within the Jenkins credential system. CloudFormation templates will be pulled from the source git-repository using the following information:

* `GitCred`: The name or ID of the Jenkins-stored git deploy-key
* `GitProjUrl`: The git-over-ssh URL to the git repository containing the CFn templates
* `GitProjBranch`: The branch or commit-tag/alias within the target repository that contains the desired version of the CFn templates.

Note that, due to a AWS-imposed character length-limit on CFN templates, for the pipelines that deploy EC2 elements &ndash; the pipeline-definitions that contain `EC2nodes` in their filenames &ndash; it will be necessary to store copies of those files in S3. Those S3 URLs will be referenced via the `TemplateUrl` parameter in those pipeline-definitions.

Further, it will be necessary to S3-host a file containing the Collibra OS administrators' public keys. That file will be referenced via the `AdminPubkeyURL` parameter and will provide the Collibra OS administrators key-based SSH access to the deployed-EC2's default-user account.

## Pipeline-Definitions

There are two groups of files: those prepended with the token `standalone` an those prepended with the token `formInput`.

The parameters mentioned in the previous section will be run-time parameters in the `standalone` pipeline-definitions and will be stored in the paramter-files referenced by the `formInput` pipeline-definitions

### `standalone` Pipeline-definitions

These pipelines are the most flexible to use, allowing the user to select all relevant parameters at build-time. 

Note that some pipelines' parameter-lists may be _quite_ lengthy. When using these definitions, and needing to re-deploy components in a way that requires changing parameter-values between deployments, the operator will _really_ want the Jenkins [Rebuilder plugin](https://github.com/jenkinsci/rebuild-plugin) to be installed on the relevant Jenkins agents. Absent this plugin, changed-value redeployment (e.g., via the standard `Build with Parameters` option) will required re-specifying _all_ parameters' desired values. The Rebuiler plugin allows the operator to only have to re-specify those values that actually need to change from a prior build.

### `formInput` Pipeline-definitions

If the Jenkins domain does not include the Rebuilder plugin, it is recommended to used these pipeline-definitions. These definitions only require the operator specify enough parameters to fetch down and parse an S3-hosted parameter-file.

Note that the Jenkins agent-nodes will need `GetObject` permissions to the S3-hosted parameter-files. It is expected that these files will typically be stored in non-public S3 buckets. Each pipeline-definition includes parameters for fetching AWS IAM user-tokens stored within the Jenkins credential-store. It is recommended that, if using these templates to deploy to different AWS accounts (e.g., "dev", "test" and "prod", each credential be bound to a specific Jenkins job-folder and that each folder be configured with per-environment pipelines referencing the folder-specific credentials.

At bare minimum, each of these pipeline-definitions will accept the following arguments:

* `AwsRegion`: (Mandatory) The region that AWS service-components will be deployed into (e.g., `us-east-1`, `us-gov-west-1`, etc.)
* `AwsSvcDomain`/`AwsSvcEndpoint`: (Optional) DNS-domain of AWS service-endpoints. Unless using private endpoints or operating in an AWS partition other than the standard commercial one, this may typically be left blank.
* `AwsCred`: (Mandatory) The name or ID of the Jenkins-stored AWS credential (as mapped within Jenkins)
* `CfnStackRoot`: (Mandatory) A prefix to apply to CFn stacks deployed by the pipeline
* `NotifyEmail`: (Optional) A valid email address to which job-failure notifications may be sent, if desired
* `ParmFileS3location` (Mandatory) The S3-URL to the parameter-file to be used by the Jenkins job

