package ai.backend.client;

import ai.backend.client.exceptions.ConfigurationException;

import java.net.MalformedURLException;
import java.net.URL;

public class Config {

    private final String accessKey;
    private final String secretKey;
    private final String apiVersionMajor;
    private final String apiVersion;
    private final String hashType;
    private final String endPoint;
    private final String userAgent;

    public Config(Builder builder) {
        accessKey = builder.accessKey;
        secretKey = builder.secretKey;
        apiVersion = builder.apiVersion;
        apiVersionMajor = builder.apiVersionMajor;
        hashType = builder.hashType;
        endPoint = builder.endPoint;
        userAgent = builder.userAgent;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getEndPoint() {
        return endPoint;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getApiVersionMajor() {
        return apiVersionMajor;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public static class Builder {
        private String accessKey = null;
        private String secretKey = null;
        private String apiVersionMajor = "v2";
        private String apiVersion = "v2.20170315";
        private String hashType = "sha256";
        private String endPoint = "https://api.sorna.io";
        private String userAgent = "BackendAI Client Library (Java/v0.1)";

        public Builder accessKey(String val) {
            accessKey = val;
            return this;
        }

        public Builder secretKey(String val) {
            secretKey = val;
            return this;
        }
        public Builder endPoint(String val) {
            endPoint = val;
            return this;
        }
        public Config build() throws ConfigurationException{

            if (accessKey == null) {
                throw new ConfigurationException("No AccessKey");
            }
            if (secretKey == null) {
                throw new ConfigurationException("No SecretKey");
            }
            try {
                new URL(String.format("%s%s", endPoint, apiVersionMajor));
            } catch (MalformedURLException e) {
                throw new ConfigurationException("Malformed endpoint URL");
            }

            return new Config(this);
        }
    }
}
