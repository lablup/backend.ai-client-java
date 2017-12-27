package ai.backend.client.exceptions;

public class AuthorizationFailureException extends BackendClientException {
    public AuthorizationFailureException() { super(); }
    public AuthorizationFailureException(String message) { super(message); }
}
