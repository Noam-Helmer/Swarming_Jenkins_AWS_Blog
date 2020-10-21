EC2_MANAGER
LIVE_MACHINES = []

pipeline {
    agent { label 'linux_executor' }
    options {
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '100'))
    }
    parameters {
        string(name: 'AMI', defaultValue: '', description: 'Windows AMI to Deploy', trim: true)
        string(name: 'KEY_PAIR', defaultValue: '', description: 'EC2 Key Pair that will be used for deployment', trim: true)
        string(name: 'MACHINE_TYPE', defaultValue: 'm5.large', description: 'Machine type to deploy', trim: true)
        string(name: 'MACHINE_NAME', defaultValue: '', description: 'Name (and label) of the machine in Jenkins', trim: true)
    }
    environment {
        AWS_REGION = 'us-west-2'
        AWS_CREDENTIALS_ID = 'aws_credentials_id'
        SWARM_CREDENTIALS_ID = 'swarm_credentials_id'
        SECURITY_GROUPS = 'SG-1,SG-2'
    }
    stages {
        stage('Setup') {
            steps {
                script {
                    EC2_MANAGER = aws.createEc2Manager(AWS_REGION, AWS_CREDENTIALS_ID)
                    def tags = [project: JOB_BASE_NAME]
                    def instanceId = ec2Manager.launchSwarmInstance(AMI, KEY_PAIR, MACHINE_TYPE, MACHINE_NAME, tags, SECURITY_GROUPS.split(','), SWARM_CREDENTIALS_ID)
                    LIVE_MACHINES.add(instanceId)
                    timeout(time: 10) {
                        node(MACHINE_NAME) { // wait until node is connected to Jenkins
                            println "New machine ${MACHINE_NAME} was deployed successfully"
                        }
                    }
                }
            }
        }
        stage('Execution') {
            agent { label MACHINE_NAME }
            steps {
                  // steps that will run on the new node
            }
        }
    }
    post {
        always {
            script {
                EC2_MANAGER.stopInstances(LIVE_MACHINES)
            }
        }
    }
}