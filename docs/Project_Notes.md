A number of issues were encountered while automating the deployment of the Collibra suite:

* The software &mdash; as of version 5.4.3 &mdash; is not able to operate on systems that have FIPS-mode enabled. This automation was written with the expectation of being deployed to an EC2 instance launched from pre-FIPSed AMI such as those produced by the [spel](https://github.com/plus3it/spel) project. This automation leverages the [watchmaker](https://github.com/plus3it/watchmaker) framework to both further harden the hosting EC2 as well as to disable FIPS-mode. This latter security posture modification will be revisited if/when the vendor indicates that their software has become FIPS-compatible
* While Collibra DGC software is advertised as supported on Red Hat 7 and CentOS 7, there are some caveats to that support. These caveats are not (as of this writing) included in the vendor documentation:
    * The services are not manage via systemd-native units. Instead, the legacy-init scripts published for Red Hat 6 and CentOS 6 are used.
    * These legacy init-scripts do not actually live in the `/etc/rc.d/init.d` directory as is expected on Red Hat 7 and CentOS 7 systems. Instead, they are linkouts to either `${COLLIBRA_SOFTWARE_HOME}/console/bin/console` or `${COLLIBRA_SOFTWARE_HOME}/agent/bin/agent`
    * If your SOPs mandate separation of OS and Application software/data, these legacy-init scripts will typically not auto-start their services. This is due to the fact that when the process that dynamically converts legacy-init scripts into systemd-managed services runs, the filesystem containing `${COLLIBRA_SOFTWARE_HOME}` is not typically mounted. Red Hat describes a work-around for this problem in Solution Document [3094591](https://access.redhat.com/solutions/3094591) (note: Red Hat subscription required to read full document). This automation implements this data-separation SOP but accounts for the needed workarounds to do so.
* The vendor's software-packaging includes an [unattended intallation mode](https://community.collibra.com/docs/install/5.4/#Installation/UnattendedInstall/to_unattended-install.htm). However, as of version 5.4.3, the unattended installer is not capable of operating, "as is" when invoked directly from an init/systemd-spawned process space. This means that without a wrapper-script, the unattended installer will not work in contexts like [cloud-init](https://cloudinit.readthedocs.io/en/latest/) or [cfn-init](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-init.html). This automation includes a suitable wrapper and executes the unattended installer within that wrapped context.
* The vendor software does not &mdash; as of version 5.4.3 &mdash; include a pre-package backup utility suitable for automation. The Web UI's backup tool is both wholly manual and does not directly result in the export of a backup image. It is necessary, instead, to write a backup utility to use the Collibra Console APIs to generate and fetch a backup-image. This project includes a `cron`-compatible backup script that will download generated backup-images straight to S3. See the [About Backups](About_Backups.md) document for more information around dependencies and usage.
* This automation implements a "one service per host" deployment design.
* This automation targets a deployment design where all service elements are deployed into private subnets and public-facing access is handled through HTTP proxies for the Console and DGC services. There are CFn templates to automate this design's deployment:
    * AWS VPC Security-group creation
    * AWS S3 bucket creation
    * AWS IAM instance-role creation
    * AWS ELB (version 2) creation
    * AWS EC2 instance-creation
    * AWS Route53 host-record creation for deployed EC2s
    * AWS Route53 alias-record creation for deployed ELBs
* These templates can be run directly from the CloudFormation CLI or web console. Additionally, this project includes Jenkins pipeline definitions to ease the use of Jenkins to control deployments.

More detailed documentation is forthcoming
