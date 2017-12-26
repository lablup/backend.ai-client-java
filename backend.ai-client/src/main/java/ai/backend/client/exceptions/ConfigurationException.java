package ai.backend.client.exceptions;

public class ConfigurationException extends BackendClientException {
    public ConfigurationException() {
        super();
    }
    public ConfigurationException(String message) {
        super(message);
    }
}