package ai.backend.client;

import ai.backend.client.exceptions.*;
import com.google.gson.*;
import okhttp3.*;

import javax.net.ssl.HttpsURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.io.IOException;

public class APIFunction {
    protected static Gson GSON;
    protected final ClientConfig config;

    private final Auth auth;
    private static SimpleDateFormat DATEFORMAT;
    private final OkHttpClient restClient = new OkHttpClient();

    static {
        DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        GSON = new Gson();
    }

    public APIFunction(ClientConfig config) {
        this.config = config;
        this.auth = new Auth(config);
    }

    public ClientConfig getClientConfig() {
        return this.config;
    }

    /**
     * Send an API request and read the response from the server.
     * It automatically parses the response body according to the server-given Content-Type header.
     *
     * @param method HTTP method name
     * @param queryString HTTP URI path and GET query parameters
     * @param requestBody HTTP request body
     * @return HTTP Response object
     * @throws BackendClientException if an API-specific error occurs such as authorization failures
     * @throws IOException if a lower-level I/O error occurs
     */

    protected Response makeRequest(String method, String queryString, RequestBody requestBody, String authBaseString)
            throws IOException, BackendClientException {
        Date now = new Date();
        if (!queryString.startsWith("/")) {
            throw new InvalidParametersException("queryString must start with a slash.");
        }
        queryString = "/" + this.config.getApiVersionMajor() + queryString;
        String dateString = String.format("%s%s", APIFunction.DATEFORMAT.format(now), "+00:00");
        String sig = this.auth.getCredentialString(
                method,
                queryString,
                now,
                String.format("%s/%s",requestBody.contentType().type(), requestBody.contentType().subtype()),
                authBaseString);
        String auth = String.format("BackendAI signMethod=HMAC-SHA256, credential=%s" ,sig);
        Request request = new Request.Builder()
                .url(String.format("%s%s", this.config.getEndPoint(), queryString))
                .method(method, requestBody)
                .addHeader("Content-Type", requestBody.contentType().toString())
                .addHeader("Content-Length", String.format("%d", requestBody.contentLength()))
                .addHeader("X-BackendAI-Version", this.config.getApiVersion())
                .addHeader("Date", dateString)
                .addHeader("User-Agent", this.config.getUserAgent())
                .addHeader("Authorization", auth)
                .build();
        Response response = this.restClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            int code = response.code();
            String errorMessage;
            try {
                JsonObject o = parseResponseAsJson(response);
                errorMessage = o.get("title").getAsString();
            } catch (IOException e) {
                errorMessage = parseResponseAsString(response);
            }
            if (code > HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                throw new ServiceUnavaliableException();
            }
            switch (code) {
                case HttpsURLConnection.HTTP_UNAUTHORIZED:
                    throw new AuthorizationFailureException(errorMessage);
                case HttpsURLConnection.HTTP_PRECON_FAILED:
                case 429: // too many requests
                    throw new ResourceLimitException(errorMessage);
                case HttpsURLConnection.HTTP_NOT_FOUND:
                    throw new KernelExpiredException(errorMessage);
                default:
                    throw new BackendClientException(String.format("%d %s", code, errorMessage));
            }
        }
        return response;
    }

    protected Response makeRequest(String method, String queryString, String requestBody)
            throws IOException, BackendClientException {
        RequestBody formBody = null;
        if (requestBody != null) {
            formBody = FormBody.create(MediaType.parse("application/json"), requestBody);
        } else {
            formBody = RequestBody.create(MediaType.parse("application/json"), new byte[0]);
        }

        return makeRequest(method, queryString, formBody, requestBody);
    }

    protected Response makeRequest(String method, String queryString, JsonObject jsonBody)
            throws IOException, BackendClientException {
        String encodedBody = GSON.toJson(jsonBody);
        return this.makeRequest(method, queryString, encodedBody);
    }

    protected Response makeRequest(String method, String queryString)
            throws IOException, BackendClientException {
        return this.makeRequest(method, queryString, "");
    }

    /**
     * Parse the given response into a JSON object.
     * NOTE: A single string, number, array are also valid JSON values, but this function assumes
     * that the response body is wrapped inside a JSON object (dictionary).
     *
     * @param response
     * @return A JSON object parsed from the response body.
     * @throws IOException
     */
    protected static JsonObject parseResponseAsJson(Response response) throws IOException {
        String contentType = response.header("Content-Type");
        if (!(contentType != null &&
                (contentType.startsWith("application/json") ||
                contentType.startsWith("application/problem+json")))) {
            throw new IOException("Expected JSON response but the server returned: " + contentType);
        }
        try {
            String body = response.body().string();
            JsonElement je = new JsonParser().parse(body);
            return je.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new IOException("Could not parse JSON from the response body.", e);
        } catch (IllegalStateException e) {
            throw new IOException("The body must contain a single JSON object at root.", e);
        }
    }

    /**
     * Decode the given response body as a string.
     *
     * @param response
     * @return A String object.
     * @throws IOException
     */
    protected static String parseResponseAsString(Response response) throws IOException {
        String contentType = response.header("Content-Type");
        if (!contentType.startsWith("text/")) {
            throw new IOException("Expected text response but the server returned: " + contentType);
        }
        String result = response.body().string();
        return result;
    }
}
