package ai.backend.client;

import ai.backend.client.exceptions.ConfigurationException;

import java.net.MalformedURLException;
import java.net.URL;

public class ClientConfig {

    private final String accessKey;
    private final String secretKey;
    private final String apiVersionMajor;
    private final String apiVersion;
    private final String hashType;
    private final String endPoint;
    private final String userAgent;
    private final String hostname;

    public ClientConfig(Builder builder) {
        accessKey = builder.accessKey;
        secretKey = builder.secretKey;
        apiVersion = builder.apiVersion;
        apiVersionMajor = builder.apiVersionMajor;
        hashType = builder.hashType;
        endPoint = builder.endPoint;
        userAgent = builder.userAgent;
        hostname = builder.hostname;
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

    public String getHostname() {
        return hostname;
    }

    public String getHashType() {
        return hashType;
    }

    public static class Builder {
        private String accessKey = null;
        private String secretKey = null;
        private String apiVersionMajor = "v2";
        private String apiVersion = "v2.20170315";
        private String hashType = "HmacSHA256";
        private String endPoint = "https://api.backend.ai";
        private String userAgent = "BackendAI Client Library (Java/v0.1)";
        private String hostname = "api.backend.ai";

        /* Methods for chained creation. */
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

        public ClientConfig build() throws ConfigurationException{

            if (accessKey == null) {
                throw new ConfigurationException("No AccessKey");
            }
            if (secretKey == null) {
                throw new ConfigurationException("No SecretKey");
            }
            try {
                String url = String.format("%s/%s", endPoint, apiVersionMajor);
                new URL(url);
            } catch (MalformedURLException e) {
                throw new ConfigurationException("Malformed endpoint URL");
            }

            return new ClientConfig(this);
        }
    }
}
