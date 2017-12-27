package ai.backend.client.exceptions;

public class ResourceLimitException extends BackendClientException {
    public ResourceLimitException() { super(); };
    public ResourceLimitException(String message) { super(message); }
}
