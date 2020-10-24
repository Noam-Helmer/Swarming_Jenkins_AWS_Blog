/**
 * Deploy a swarm instance and executed the given closure on it
 * @param params A map of key values parameters to be used, supports the following parameters:
 * String region - AWS region in which all operations will be executed
 * String credentialsId - Jenkins defined AWS credentials ID
 * String amiId - The requested ami id
 * String KeyPair - aws key pair name that will be used to launch the instance
 * String type - The requested instance type. Examples: m4.large, t1.micro
 * String instanceName - The name (Name Tag) for the new instance - cannot contain whitespaces
 * Map tags - Tags that will be added to the new instance. The 'Name' tag is added by default
 * List securityGroups -  List of security groups IDs that will be assigned to the new instance
 * String swarmCredentialsId - Jenkins defined credentials ID that allows remote connections
 * @param body The Closure that will be executed on the deployed node
 *
 */
def call(Map params, Closure body){
    def EC2_MANAGER = aws.createEc2Manager(params.region, params.credentialsId)
    def instanceId
    try {
        instanceId = ec2Manager.launchSwarmInstance(params.ami, params.keyPair, params.type, params.instanceName, params.tags, params.securityGroups, params.swarmCredentialsId)
        timeout(time: 10) {
            node(params.instanceName) { // wait until node is connected to Jenkins
                println "New machine ${MACHINE_NAME} was deployed successfully"
            }
        }
        node(params.instanceName) {
            body()
        }
    }
    finally {
        if(instanceId) {
            EC2_MANAGER.terminateInstances([instanceId])
        }
    }
}