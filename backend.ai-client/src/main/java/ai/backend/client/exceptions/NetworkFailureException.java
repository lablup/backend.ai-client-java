package ai.backend.client.exceptions;

import sun.nio.ch.Net;

public class NetworkFailureException extends BackendClientException {
    public NetworkFailureException() { super(); }
    public NetworkFailureException(String message) { super(message); }
}
