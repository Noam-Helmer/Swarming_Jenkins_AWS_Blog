package aws

/**
 * LambdaManager provides a wrapper for the AWS LAMBDA service.
 * The LambdaManager uses the AWS command line interface (AWS CLI) for executing the api calls.
 */
class LambdaManager extends AwsService implements Serializable {
    /**
     * The name of the AWS service represented by this class
     * <strong>This name will be used in the AWS CLI as the execution command</strong>
     */
    private static final String SERVICE_NAME = "lambda";

    /**
     * Construct a new instance of the LambdaManager class, oriented for a specific region with a specific user.
     *
     * @param script Jenkins Pipeline Script context
     * @param region AWS region in which all operations will be executed
     * @param credentialsId Jenkins defined AWS credentials ID - User should have Full Lambda permissions
     */
    LambdaManager(Script script, String region, String credentialId) {
        super(script, region, credentialId)
    }

    /**
     * Create a LambdaManager object oriented for executing Lambda operations in a specific region with a specific user.
     *
     *  @param script Jenkins Pipeline Script context
     *  @param region AWS region in which all operations will be executed
     *  @param credentialsId Jenkins defined AWS credentials ID
     *  @return New instance of the LambdaManager initialized with the given parameters
     */
    static LambdaManager create(Script script, String region, String credentialsId) {
        return new LambdaManager(script, region, credentialsId)
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
     * Get a list of event source mappings (Lambda triggers) according to the given filters.
     * If no filter is set, a list of all event source mappings in the current region will be returned.
     * <strong>Event source mappings are supported on the following AWS services: Kinesis, DynamoDB Streams, Simple Queue Service.</strong>
     *
     * @param  functionName - The name or the Amazon Resource Name (ARN) of the Lambda function. If not set (or empty), query for all lambda functions
     * @param  eventSourceArn - The Amazon Resource Name (ARN) of the event source.  If not set (or empty), query for all event sources
     * @return List of event source mappings, each represented by a dictionary with the following keys:
     * UUID, StateTransitionReason, LastModified, BatchSize, EventSourceArn, FunctionArn, State
     */
    Map getEventSourceMappings(String functionName = "", String eventSourceArn = ""){
        String cmdArgs = "--no-paginate"
        if (eventSourceArn) {
            cmdArgs += " --event-source-arn $eventSourceArn"
        }
        if (functionName) {
            cmdArgs += " --function-name $functionName"
        }

        if(functionName || eventSourceArn) {
            println "Retrieving all event source mappings that match:\n" +
                    "Function Name/ARN: ${functionName ?: 'All'}\nEvent Source ARN: ${eventSourceArn ?: 'ALL'}"
        }
        else {
            println "Retrieving all event source mappings in the ${this.region} region"
        }

        // Retrieve the mappings
        String output = runApiCommand('list-event-source-mappings', cmdArgs)
        // Read the command output
        Map eventMappings = script.readJSON text: output
        println "Event Source Mappings: ${eventMappings.EventSourceMappings}"
        return eventMappings.EventSourceMappings
    }

    /**
     * Enable or Disable LAMBDA triggers (event-source-mapping) that matches the given filters.
     * <strong>At least one of the filters 'Event Source ARN' or 'Function Name/ARN' must be set</strong>
     *
     * @param  functionName - The name or the Amazon Resource Name (ARN) of the Lambda function. If not set (or empty), query for all lambda functions
     * @param  eventSourceArn - The Amazon Resource Name (ARN) of the event source.  If not set (or empty), query for all event sources
     * @param enabled - True for enabling the trigger. False for Disabling the trigger
     */
    void setTriggersState(String functionName, String eventSourceArn, boolean enabled) {
        assert functionName || eventSourceArn : "At least one filter must be set: 'Event Source ARN' or 'Function Name/ARN'"
        // Get all matching triggers, and set the state for all of them
        getEventSourceMappings(functionName, eventSourceArn).each {
            setTriggerState(it.UUID, enabled)
        }
    }

    /**
     * Enable or Disable a LAMBDA trigger (event-source-mapping) that matches the given UUID.
     *
     * @param uuid - UUID of the trigger (event-source-mapping)
     * @param enabled - True for enabling the trigger. False for Disabling the trigger
     */
    void setTriggerState(String uuid, boolean enabled) {
        Integer waitIntervalSeconds = 10
        Integer timeoutSeconds = 120

        String expectedState = enabled ? "Enabled" : "Disabled"
        String cmdArg = enabled ? "--enabled" : "--no-enabled"

        // Update the trigger state
        println "Setting trigger with UUID: $uuid to state '$expectedState'"
        String output = runApiCommand('update-event-source-mapping', " --uuid $uuid $cmdArg")
        println "Output from update event command: $output"

        // Extract parameters from update command output
        Map eventSource = script.readJSON text: output
        String currentState = eventSource.State

        // Query the trigger until it reaches desired state or timeout
        long timeoutExpiredSeconds = System.currentTimeSeconds() + timeoutSeconds
        while (System.currentTimeSeconds() <= timeoutExpiredSeconds && currentState != expectedState) {
            println "Current trigger state is: $currentState\nCheck trigger state again in $waitIntervalSeconds seconds..."
            script.sleep(waitIntervalSeconds)
            // Query the trigger again to get the updated state
            output = runApiCommand('get-event-source-mapping', " --uuid $uuid")
            currentState = script.readJSON(text: output).State
        }

        if (currentState != expectedState) {
            // Throw an AbortException and notify the pipeline that an error has occurred
            script.error("Trigger is in state '$currentState' after $timeoutSeconds seconds while it is expected to be in state $expectedState")
        }
        println "Trigger has reached the desired state: $currentState"
    }

    /**
     * Disable a LAMBDA trigger (event-source-mapping) that matches the given UUID.
     *
     * @param uuid - UUID of the trigger (event-source-mapping)
     * @param eventSourceArn - Event source ARN
     */
    void disableTrigger(String uuid, String eventSourceArn) {
        setTriggerState(uuid, false)
    }

    /**
     * Enable a LAMBDA trigger (event-source-mapping) that matches the given UUID.
     *
     * @param uuid - UUID of the trigger (event-source-mapping)
     * @param eventSourceArn - Event source ARN
     */
    void enableTrigger(String uuid, String eventSourceArn) {
        setTriggerState(uuid, true)
    }
}
