package ai.backend.client.exceptions;

public class InvalidParametersException extends BackendClientException {
    public InvalidParametersException() { super(); }
    public InvalidParametersException(String message) { super(message); }
}
