package ai.backend.client.exceptions;

public class BaseException extends RuntimeException{
    public BaseException() {
        super();
    }
    public BaseException(String message) {
        super(message);
    }
}
