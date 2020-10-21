import aws.AwsService
import aws.Ec2Manager
import aws.SqsInstance
import aws.LambdaManager

/**
 * Create a new {@link aws.Ec2Manager} object oriented for executing EC2 operations in a specific region with a specific user.
 *
 *  @param region AWS region in which all operations will be executed
 *  @param credentialsId Jenkins defined AWS credentials ID
 *  @return New {@link aws.Ec2Manager} initialized with the given parameters
 */
def createEc2Manager(String region, String credentialsId) {
    return Ec2Manager.create(this, region, credentialsId)
}

/**
 * Create a new {@link aws.SqsInstance} object oriented for executing SQS operations in a specific region with a specific user.
 *
 *  @param region AWS region in which all operations will be executed
 *  @param credentialsId Jenkins defined AWS credentials ID
 *  @param queueUrl URL of the queue on which all operations will be executed
 *  @return New {@link aws.SqsInstance} instance initialized with the given parameters
 */
def createSqsInstance(String region, String credentialsId, String queueUrl) {
    return SqsInstance.create(this, region, credentialsId, queueUrl)
}

/**
 *  Create a new {@link aws.LambdaManager} object oriented for executing LAMBDA operations in a specific region with a specific user.
 *
 *  @param region AWS region in which all operations will be executed
 *  @param credentialsId Jenkins defined AWS credentials ID
 *  @return New {@link aws.LambdaManager} initialized with the given parameters
 */
def createLambdaManager(String region, String credentialsId) {
    return LambdaManager.create(this, region, credentialsId)
}

/**
 * Run the given api command using the AWS command line interface.
 *
 * @param region AWS region in which all operations will be executed
 * @param credentialsId Jenkins defined AWS credentials ID
 * @param command AWS command to execute (via AWS CLI)
 * @return The output of the command as string
 */
def call(String region, String credentialsId, String command){
    assert region in AwsService.awsRegions: "Region parameter has an invalid value: ${region}\nSuported regions are: ${AwsService.awsRegions}"
    assert credentialsId: "CredentialsId parameter cannot be empty"
    assert command: "Command parameter cannot be empty"
    withAWS(region: region, credentials: credentialsId) {
        return sh(script: "aws ${command}", returnStdout: true)
    }
}