package foo;


/**
 * Used as a return value for the API methods
 * @see foo.PostEndpoint
 *
 * in the Endpoints Frameworks documentation, entity types are synonymous with Java Beans. The classes that you define for use in your API must
 * 		have a public constructor that takes no arguments
 * 		control access to private properties using getters and setters. Additionally, each setter must take only one parameter
 *
 */
public class ReturnMessage {
    public String message;
    public ReturnMessage(){}
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
