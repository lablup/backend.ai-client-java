package ai.backend.client.exceptions;

public class NetworkFailureException extends BackendClientException {
    public NetworkFailureException() { super(); }
    public NetworkFailureException(String message) { super(message); }
}
