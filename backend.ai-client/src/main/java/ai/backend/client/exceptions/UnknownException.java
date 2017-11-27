package ai.backend.client.exceptions;

public class UnknownException extends BaseException{
    public UnknownException() {
        super();
    }

    public UnknownException(String message) {
        super(message);
    }
}