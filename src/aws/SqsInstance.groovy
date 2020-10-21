package aws

/**
 * SqsInstance provides a wrapper for a single instance of the the AWS SQS service.
 * The SqsInstance uses the AWS command line interface for executing the api calls.
 */
class SqsInstance extends AwsService implements Serializable {
    /**
     * The name of the AWS service represented by this class
     * <strong>This name will be used in the AWS CLI as the execution command</strong>
     */
    private static final String SERVICE_NAME = "sqs";

    /**
     * The URL of the AWS SQS queue on which all operations will be executed.
     * <strong>Required value, may not be null!</strong>
     */
    protected final String queueUrl

    /**
     * Construct a new instance of the SqsManager class, oriented for a specific region with a specific user.
     *
     * @param script Jenkins Pipeline Script context
     * @param region AWS region in which all operations will be executed
     * @param credentialsId Jenkins defined AWS credentials ID
     * @param queueUrl URL of the queue on which all operations will be executed
     */
    SqsInstance(Script script, String region, String credentialId, String queueUrl) {
        super(script, region, credentialId)
        assert queueUrl: "The queue URL cannot be empty"
        this.queueUrl = queueUrl
    }

    /**
     * Create a SqsInstance object oriented for executing SQS operations in a specific region with a specific user.
     *
     *  @param script Jenkins Pipeline Script context
     *  @param region AWS region in which all operations will be executed
     *  @param credentialsId Jenkins defined AWS credentials ID
     *  @param queueUrl URL of the queue on which all operations will be executed
     *  @return New instance of the SqsManager initialized with the given parameters
     */
    static SqsInstance create(Script script, String region, String credentialsId, String queueUrl) {
        return new SqsInstance(script, region, credentialsId, queueUrl)
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
     * Run an SQS command with the given parameters using the AWS command line interface.
     *
     * @param command AWS CLI SQS command to execute
     * @param args Arguments for the given command
     */
    @Override
    String runApiCommand(String command, String args = '') {
        super.runApiCommand(command, "--queue-url $queueUrl $args")
    }

    /**
     * Retrieves one or more messages (up to 10), from the specified queue.
     * A message that isn't deleted or a message whose visibility isn't extended before the visibility timeout expires counts as a failed receive.
     * Depending on the configuration of the queue, the message might be sent to the dead-letter queue.
     * Attention: this function retrieves <strong>all</strong> Attribute Names and Message Attribute Names of the messages.
     *
     * @param maxNumberOfMessages The maximum number of messages to return. Valid values: 1 to 10
     * Amazon SQS never returns more messages than this value (however, fewer messages might be returned)
     * @param visibilityTimeout The duration (in seconds) that the received messages are hidden from subsequent
     * retrieve requests after being retrieved by a ReceiveMessage request
     * @return Map containing all the received messages information
     * Output format is available at https://docs.aws.amazon.com/cli/latest/reference/sqs/receive-message.html
     */
    Map receiveMessage(Integer maxNumberOfMessages, Integer visibilityTimeout = 30) {
        println "Receiving maximum $maxNumberOfMessages from queue $queueUrl"
        assert maxNumberOfMessages >= 1 && maxNumberOfMessages <= 10 : "Invalid number of messages: $maxNumberOfMessages. Valid values: 1 to 10"

        // Receive the queue messages
        String messagesJson = runApiCommand('receive-message', " --attribute-names All --message-attribute-names All \
                                            --max-number-of-messages $maxNumberOfMessages --visibility-timeout $visibilityTimeout")
        // Read the command output
        Map messageInfo = script.readJSON text: messagesJson
        println "Recived messagese infromation: $messageInfo"
        return messageInfo
    }


    /**
     * Deletes the specified message from the specified queue.
     * To select the message to delete, use the ReceiptHandle of the message (not the MessageId which you receive when you send the message).
     * <strong>Amazon SQS can delete a message from a queue even if a visibility timeout setting causes the message to be locked by another consumer</strong>
     *
     * @param receiptHandle The receipt handle associated with the message to delete.
     */
    void deleteMessage(String receiptHandle){
        println "Deleting message with receipt handle: $receiptHandle"
        runApiCommand('delete-message', "--receipt-handle $receiptHandle")
    }

    /**
     * Deletes all the messages in a queue (The message deletion process takes up to 60 seconds)
     * Warning - When you use the PurgeQueue action, you can't retrieve any messages deleted from a queue.
     * Messages sent to the queue before you call PurgeQueue might be received but are deleted within the next minute.
     * Messages sent to the queue after you call PurgeQueue might be deleted while the queue is being purged.
     */
    void purgeQueue(){
        println "Purgeing queue: $queueUrl"
        runApiCommand('purge-queue')
    }

    /**
     * Delivers a message to the specified queue.
     * Supports only attributes with String Values
     * A message can include only XML, JSON, and unformatted text.
     * The following Unicode characters are allowed: #x9 | #xA | #xD | #x20 to #xD7FF | #xE000 to #xFFFD | #x10000 to #x10FFFF
     *
     * @param body The message body to send. The maximum string size is 256 KB.
     * @param messageAttributes Map of string attributes, key represents the attribute name and the value represents the string value.
     * @param delaySeconds The length of time, in seconds, for which to delay a specific message. Valid values: 0 to 900.
     * @return The message Id of the sent message
     */
    String sendStringMessage(String body, Map messageAttributes, Integer delaySeconds = 0){
        println "Sending message to queue: $queueUrl"

        // Create the attributes argument
        List attributesList = messageAttributes.collect { /${it.key}=StringValue=${it.value},DataType=string/ }.join ","
        String attributesCommand = attributesList ? " --message-attributes $attributesList" : ''

        // Send message to queue
        String messageJson = runApiCommand('send-message', "--message-body $body --delay-seconds $delaySeconds$attributesCommand")

        // Read the command output
        Map messageInfo = script.readJSON text: messageJson
        println "Sent message info: $messageInfo"
        String messageId = messageInfo['MessageId']
        println "Message ID of the sent message: $messageId"
        return messageId
    }
}
