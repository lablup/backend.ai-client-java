package ai.backend.client.exceptions;

public class BackendClientException extends RuntimeException {

    public BackendClientException() {
        super();
    }

    public BackendClientException(String message) {
        super(message);
    }

    public BackendClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
