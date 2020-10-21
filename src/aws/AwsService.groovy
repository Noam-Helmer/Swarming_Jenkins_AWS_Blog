package aws

/**
 * AwsService provides an abstract wrapper for an AWS service.
 * Each implementing subclass will use the AWS command line interface for executing service specific api calls.
 */
abstract class AwsService implements Serializable {
    /**
     * Jenkins Pipeline script context.
     * <strong>Required value, may not be null!</strong>
     */
    protected final Script script

    /**
     * AWS region in which all operations will be executed.
     * <strong>Required value, may not be null!</strong>
     */
    protected final String region

    /**
     * Jenkins defined AWS credentials ID.
     * Given credentials must have the proper access rights for executing all needed operations.
     * <strong>Required value, may not be null!</strong>
     */
    protected final String credentialsId

    /**
     * Supported AWS regions
     */
    final static List awsRegions = ['us-east-2', 'us-east-1','us-west-1', 'us-east-1','us-west-2', 'us-east-1','ap-east-1', 'ap-south-1',
                                    'ap-northeast-1', 'ap-northeast-2','ap-northeast-2', 'ap-southeast-1', 'ap-southeast-2','ca-central-1',
                                    'eu-central-1','eu-west-1', 'eu-west-2','eu-west-3', 'eu-north-1','me-south-1', 'sa-east-1']

    /**
     * Initialize the AwsService instance with the relevant parameters.
     *
     *  @param script Jenkins Pipeline Script context
     *  @param region AWS region in which all operations will be executed
     *  @param credentialsId Jenkins defined AWS credentials ID
     */
    AwsService(Script script, String region, String credentialsId) {
        assert script: "The script parameter cannot be empty"
        assert region in awsRegions : "Region parameter has an invalid value: $region\nSuported regions are: $awsRegions"
        assert credentialsId: "CredentialsId parameter cannot be empty"

        this.script = script
        this.region = region
        this.credentialsId = credentialsId
    }

    /**
     * Returns The name of the AWS service that the implementing class represents
     * <strong>This name will be used in the AWS CLI as the execution command</strong>
     */
    abstract String getServiceName();

    /**
     * Run an api command with the given parameters using the AWS command line interface.
     *
     * @param command AWS command to execute (AWS CLI)
     * @param args Arguments for the given command
     */
    String runApiCommand(String command, String args) {
        assert command : "The 'command' parameter Cannot be empty"
        script.withAWS(region: region, credentials: credentialsId) {
            return script.sh(script: "aws $serviceName $command $args",
                             label: "${serviceName.toUpperCase()}: $command", returnStdout: true)
        }
    }
}
