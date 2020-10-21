
properties([buildDiscarder(logRotator( daysToKeepStr: '30', numToKeepStr: '100')),
            parameters([string(name: 'AMI', defaultValue: '', description: 'Windows AMI to Deploy', trim: true),
                        string(name: 'KEY_PAIR', defaultValue: '', description: 'EC2 Key Pair that will be used for deployment', trim: true),
                        string(name: 'MACHINE_TYPE', defaultValue: 'm5.large', description: 'Machine type to deploy', trim: true),
                        string(name: 'MACHINE_NAME', defaultValue: '', description: 'Name (and label) of the machine in Jenkins', trim: true)])])

def AWS_REGION = 'us-west-2'
def AWS_CREDENTIALS_ID = 'aws_credentials_id'
def SWARM_CREDENTIALS_ID = 'swarm_credentials_id'
def SECURITY_GROUPS = ['SG-1','SG-2']

def EC2_MANAGER = aws.createEc2Manager(AWS_REGION, AWS_CREDENTIALS_ID)
def LIVE_MACHINES = []

node('linux_executor') {
    try {
        stage('Setup') {
            def tags = [project: JOB_BASE_NAME]
            def instanceId = ec2Manager.launchSwarmInstance(AMI, KEY_PAIR, MACHINE_TYPE, MACHINE_NAME, tags, SECURITY_GROUPS, SWARM_CREDENTIALS_ID)
            LIVE_MACHINES.add(instanceId)
            timeout(time: 10) {
                node(MACHINE_NAME) { // wait until node is connected to Jenkins
                    println "New machine ${MACHINE_NAME} was deployed successfully"
                }
            }
        }
        stage('Execution') {
           node(MACHINE_NAME) {
               // steps that will run on the new node
           }
        }
    }
    finally {
        EC2_MANAGER.stopInstances(LIVE_MACHINES)
    }
}