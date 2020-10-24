
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
    stage('Execution') {
        def tags = [project: JOB_BASE_NAME]
        swarm(region:AWS_REGION, credentialsId:AWS_CREDENTIALS_ID, amiId:AMI, KeyPair:KEY_PAIR, type:MACHINE_TYPE,
                instanceName:MACHINE_NAME, tags:tags, securityGroups:SECURITY_GROUPS, swarmCredentialsId:SWARM_CREDENTIALS_ID) {
            // steps that will run on the new node
        }
    }
}