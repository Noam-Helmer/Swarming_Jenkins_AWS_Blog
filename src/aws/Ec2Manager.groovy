package aws

/**
 * Ec2Manager provides a wrapper for the AWS EC2 service.
 * The Ec2Manager uses the AWS command line interface for executing the api calls.
 */
class Ec2Manager extends AwsService implements Serializable {
    /**
     * The name of the AWS service represented by this class
     * <strong>This name will be used in the AWS CLI as the execution command</strong>
     */
    private static final String SERVICE_NAME = "ec2";

    /**
     * Enabling T2/T3 Unlimited (setting Cpu Credits to Unlimited) allows applications to burst beyond the baseline for as long as needed at any time.
     * If the average CPU utilization of the instance is at or below the baseline, the hourly instance price automatically covers all usage.
     * Otherwise, all usage above baseline is billed. Therefore this feature will be turned off by default.
     * <strong>This configuration is relevant only for instance types from the T2 or T3 family</strong>
     */
    public Boolean CpuCredits = false

    /**
     * Construct a new instance of the Ec2Manager class, oriented for a specific region with a specific user.
     *
     *  @param script Jenkins Pipeline Script context
     *  @param region AWS region in which all operations will be executed
     *  @param credentialsId Jenkins defined AWS credentials ID
     */
    Ec2Manager(Script script, String region, String credentialsId) {
        super(script, region, credentialsId)
    }

    /**
     * Create a Ec2Manager object oriented for executing EC2 operations in a specific region with a specific user.
     *
     *  @param script Jenkins Pipeline Script context
     *  @param region AWS region in which all operations will be executed
     *  @param credentialsId Jenkins defined AWS credentials ID
     *  @return New instance of the Ec2Manager initialized with the given parameters
     */
    static Ec2Manager create(Script script, String region, String credentialsId) {
        return new Ec2Manager(script, region, credentialsId)
    }

    /**
     * Returns The name of the AWS service that the implementing class represents
     * <strong>This name will be used in the AWS CLI as the execution command</strong>
     */
    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    /**
     * Stops running EC2 instances.
     *
     * @param instanceIds List of Instance IDs
     */
    void stopInstances(List instanceIds) {
        script.println "Stopping EC2 instances with ids: $instanceIds"
        runApiCommand('stop-instances', "--instance-ids ${instanceIds.join(' ')}")
    }

    /**
     * Terminates EC2 instances.
     * This operation is idempotent; if you terminate an instance more than once, each call succeeds.
     * Terminated instances remain visible after termination (for approximately one hour).
     * If you specify multiple instances and the request fails (for example, because of a single incorrect instance ID), none of the instances are terminated.
     *
     * @param instanceIds List of Instance IDs
     */
    void terminateInstances(List instanceIds) {
        script.println "Terminating EC2 instances with ids: $instanceIds"
        runApiCommand('terminate-instances', "--instance-ids ${instanceIds.join(' ')}")
    }

    /**
     * Reboots EC2 instances.
     * This operation is asynchronous; it only queues a request to reboot the specified instances.
     * If an instance does not cleanly shut down within four minutes, Amazon EC2 performs a hard reboot.
     *
     * @param instanceIds List of Instance IDs
     */
    void rebootInstances(List instanceIds) {
        script.println "Rebooting EC2 instances with ids: $instanceIds"
        runApiCommand('reboot-instances', "--instance-ids ${instanceIds.join(' ')}")
    }

    /**
     * Retrieve a JPG-format screenshot of a running instance to help with troubleshooting.
     * The file will be created inside the current workspace with the given name or a generated name if none was given.
     *
     * @param instanceId The Instance ID of the running instance in which to take the screenshot from
     * @param fileName Optional file name (or path) to in which the screenshot will be saved to
     * @return The name of the retrieved screenshot file
     */
    String getScreenshot(String instanceId, String fileName = '') {
        String imageFile = fileName ?: "Screenshot_${new Date().getTime()}.jpg"
        runApiCommand('get-console-screenshot', "--instance-id $instanceId --query \"ImageData\" --output text > base64image")

        // Base64 Decode the output file
        script.sh("base64 --decode base64image > $imageFile")
        return imageFile
    }

    /**
     * Adds or overwrites the specified tags for the specified Amazon EC2 resource or resources.
     * Each resource can have a maximum of 50 tags.
     *
     * @param resourceId The ID of the resource to be tagged
     * @param tags Map of tags, the key represents the tag key and the value represents and optional tag value
     */
    void tagResource(String resourceId, Map tags) {
        script.println "Tagging resource with id ${resourceId} with the following tags: ${tags}"
        def tagsCommand = tags.collect { /$it.key="$it.value"/ }.join " "
        runApiCommand('create-tags', "--resources $resourceId --tags ${toKeyValue(tags)}")
    }

    /**
     * Launches an EC2 instance using the given AMI.
     * To ensure faster instance launches, break up large requests into smaller batches.
     * For example, create five separate launch requests for 100 instances each instead of one launch request for 500 instances.
     *
     * @param amiId The requested ami id
     * @param KeyPair aws key pair name that will be used to launch the instance
     * @param type The requested instance type. Examples: m5.large, t3.micro
     * @param name The name (Name Tag) for the new instance
     * @param tags Map of tags that will be added to the new instance. The 'Name' tag is added by default if not given
     * @param securityGroups List of security groups IDs that will be assigned to the new instance
     * @param userDataFile Path to a local user data file (Not Mandatory)
     * @return The instance Id of the deployed instance
     */
    String launchInstance(String amiId, String KeyPair, String type, String name, Map tags, List securityGroups, String userDataFile = null) {
        script.println "Deploying EC2 $type instance from ami '$amiId'"
        String userDataFlag = userDataFile ? "--user-data file://$userDataFile " : ''

        // Create the tags argument
        tags.Name = name
        String tagsList = tags.collect { /$it.key="$it.value"/ }.join ','
        String tagsCommand = "'ResourceType=instance,Tags=[$tagsList]' 'ResourceType=volume,Tags=[$tagsList]'"

        // Set the T2/T3 Cpu Credits for relevant types
        String tTypeConfig = type.toLowerCase().startsWith('t') ? " --credit-specification CpuCredits=${CpuCredits ? 'unlimited ' : 'standard'}" : ''

        // Deploy the EC2 instance
        String instanceJson = runApiCommand('run-instances', "--image-id $amiId --count 1 --instance-type $type --key-name $KeyPair \
                                            $userDataFlag--security-group-ids ${securityGroups.join(' ')} --tag-specifications $tagsCommand$tTypeConfig")
        // Read the command output
        Map instanceInfo = script.readJSON text: instanceJson
        script.println "New instance infromation: $instanceInfo"
        String instanceId = instanceInfo['Instances']['InstanceId'][0]
        script.println "Instance ID of the new deployed instance: $instanceId"
        return instanceId
    }

    /**
     * Launches a Swarm EC2 instance that will connect to the jenkins using the swarm-client.
     * Attention - This method assumes that the swarm client is already present on the AMI template,
     * and that an appropriate schedule trigger was already configured for running it.
     *
     * @param amiId The requested ami id
     * @param KeyPair aws key pair name that will be used to launch the instance
     * @param type The requested instance type. Examples: m4.large, t1.micro
     * @param instanceName The name (Name Tag) for the new instance - cannot contain whitespaces
     * @param tags Map of tags that will be added to the new instance. The 'Name' tag is added by default if not given
     * @param securityGroups List of security groups IDs that will be assigned to the new instance
     * @param swarmCredentialsId Jenkins defined credentials ID that allows remote connections
     * @param swarmClientFolder The folder in which the swarm-client.jar resides. Example: C:\Swarm
     * @return The instance id of the deployed instance
     */
    String launchSwarmInstance(String amiId, String KeyPair, String type, String name, Map tags, List securityGroups, String swarmCredentialsId, String swarmClientPath = 'C:\\Swarm') {
        script.println "Deploying an EC2 Jenkins SWARM instance named: $name"
        assert !name.contains(' '): "ERROR - Instance deployment failed, Swarm instance name cannot contain whitespaces"
        String userDataFileName = "${name}_userData.txt"

        // Create the user data script
        script.withCredentials([script.usernamePassword(credentialsId: swarmCredentialsId, usernameVariable: 'SWARM_USER', passwordVariable: 'SWARM_PASS')]) {
            String userData = """\
<script>
echo java.exe -jar $swarmClientFolder\\swarm-client.jar -master $script.env.JENKINS_URL -username $script.env.SWARM_USER -password $script.env.SWARM_PASS -labels $name -name $name -disableClientsUniqueId -mode exclusive -executors 1 -fsroot C:\\Jenkins > $swarmClientFolder\\swarm-client.cmd
</script>"""
            // Create the user data file
            script.writeFile(file: userDataFileName, text: userData)
        }

        // Deploy the machine
        return launchInstance(amiId, KeyPair, type, name, tags, securityGroups, userDataFileName)
    }
}
