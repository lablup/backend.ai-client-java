package ai.backend.client.exceptions;

public class KernelExpiredException extends RuntimeException {
    public KernelExpiredException() { super(); }
    public KernelExpiredException(String message) { super(message); }
}
