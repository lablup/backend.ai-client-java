package ai.backend.client.exceptions;

public class UnknownException extends BackendClientException {
    public UnknownException() {
        super();
    }

    public UnknownException(String message) {
        super(message);
    }
}