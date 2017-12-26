package ai.backend.client;

import ai.backend.client.exceptions.*;
import ai.backend.client.values.*;
import com.google.gson.*;
import okhttp3.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;


public class Kernel {
    private final ClientConfig config;
    private final String kernelType;
    private final String sessionToken;
    private String runId;
    private final Auth auth;
    private static SimpleDateFormat DATEFORMAT;
    private static Gson GSON;
    private final OkHttpClient restClient = new OkHttpClient();

    static {
        DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        GSON = new Gson();
    }

    private Kernel(String sessionToken, String kernelType, ClientConfig config) throws ServiceUnavaliableException, NetworkFailException, UnknownException{
        String token;
        if(sessionToken == null) {
            token = generateSessionToken();
        } else {
            token = sessionToken;
        }

        this.config = config;
        this.auth = new Auth(config);
        this.kernelType = kernelType;
        this.sessionToken = createKernelIfNotExists(token);
        this.runId = generateRunId();
    }

    /**
     * Prepare a compute session.
     * The session may be reused if the user provides the same session token across different kernel objects before
     * the kernel finishes or is expired.
     *
     * @param sessionToken User-defined session identifier (8 to 64 bytes). If null, it will use a random-generated session ID.
     * @param kernelType The base container image used for computation (e.g., "python:latest").
     * @param config The client-side configuration object to use.
     * @return A kernel object representing the compute session.
     */
    public static Kernel getOrCreateInstance(String sessionToken, String kernelType, ClientConfig config) {
        return new Kernel(sessionToken, kernelType,  config);
    }

    /**
     * Execute user codes in this compute session.
     *
     * @param mode The mode of execution.
     * @param code A code snippet or user-input string depending on the mode.
     * @param opts An optional object specifying batch-mode build and execution commands.
     * @return The execution result. Depending on its status, you should call execute() again and/or process the output.
     * @throws BackendClientException
     */
    public ExecutionResult execute(ExecutionMode mode, String code, JsonObject opts) throws BackendClientException {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mode", mode.name());
        jsonObject.addProperty("code", code);
        if (opts != null) {
            jsonObject.add("opts", opts);
        }
        jsonObject.addProperty("runId", this.runId);
        String requestBody = GSON.toJson(jsonObject);
        try {
            Response resp = this.request("POST", String.format("/%s/kernel/%s", this.config.getApiVersionMajor(), this.sessionToken), requestBody);
            JsonObject result = this.parseResponseAsJson(resp);
            return new ExecutionResult(result);
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }
    }

    /**
     * Terminate and destroy the compute session.
     *
     * @throws BackendClientException
     */
    public void destroy() throws BackendClientException {
        try {
            this.request("DELETE", String.format("/%s/kernel/%s", this.config.getApiVersionMajor(), this.sessionToken), "");
            // TODO: support returned statisitics
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }
    }

    /**
     * Restart the compute session with keeping its working directory and mounted volumes.
     *
     * @throws BackendClientException
     */
    public void refresh() throws BackendClientException {
        try {
            this.request("PATCH", String.format("/%s/kernel/%s", this.config.getApiVersionMajor(), this.sessionToken), "");
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }
    }

    /**
     * Send an interrupt signal (SIGINT) to the main program of the compute session.
     * NOTE: This does NOT guarantee the interruption depending on the actual kernel status and runtime implementation.
     *
     * @throws BackendClientException
     */
    public void interrupt() throws BackendClientException {
        try {
            this.request("POST", String.format("/%s/kernel/%s/interrupt", this.config.getApiVersionMajor(), this.sessionToken), "");
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }
    }

    private String verifyKernel() throws BackendClientException {
        try {
            Response resp = this.request("GET", String.format("/%s/kernel/%s", this.config.getApiVersionMajor(), this.sessionToken), "");
            JsonObject result = this.parseResponseAsJson(resp);
            if (result.has("lang")) {
                return result.get("lang").getAsString();
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }
    }

    /**
     * Create the kernel for a compute session.
     *
     * @param token User-defined or randomized session ID.
     * @return The session ID which can be used as the identifier afterwards (currently this is same to token)
     * @throws BackendClientException
     */
    public String createKernelIfNotExists(String token) throws BackendClientException {
        String kernelId;
        JsonObject args = new JsonObject();
        args.addProperty("lang", this.kernelType);
        args.addProperty("clientSessionToken", token);
        JsonObject resourceLimits = new JsonObject();
        resourceLimits.addProperty("maxMem", 0);
        resourceLimits.addProperty("timeout", 0);
        args.add("resourceLimits", resourceLimits);

        String requestBody = GSON.toJson(args);

        try {
            Response resp = this.request("POST", String.format("/%s/kernel/create", this.config.getApiVersionMajor()), requestBody);
            JsonObject result = this.parseResponseAsJson(resp);
            if(result.has("kernelId")) {
                kernelId = (result.get("kernelId").getAsString());
            } else {
                throw new UnknownException("Malformed JSON");
            }
            return kernelId;
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }
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
    private Response request(String method, String queryString, String requestBody)
            throws IOException, BackendClientException {
        Date now = new Date();
        String dateString = String.format("%s%s", this.DATEFORMAT.format(now), "+00:00");
        String sig = this.auth.getCredentialString(method, queryString, now, requestBody);
        String auth = String.format("BackendAI signMethod=HMAC-SHA256, credential=%s" ,sig);
        RequestBody formBody = null;
        if (requestBody != null) {
            formBody = FormBody.create(MediaType.parse("application/json; charset=utf-8"), requestBody);
        }
        Request request = new Request.Builder()
                .url(String.format("%s%s", this.config.getEndPoint(), queryString))
                .method(method,formBody)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Content-Length", String.format("%d", requestBody.length()))
                .addHeader("X-BackendAI-Version", this.config.getApiVersion())
                .addHeader("Date", dateString)
                .addHeader("User-Agent", this.config.getUserAgent())
                .addHeader("Authorization", auth)
                .build();
        Response response = this.restClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            int code = response.code();
            if(code > HttpsURLConnection.HTTP_INTERNAL_ERROR){
                throw new ServiceUnavaliableException();
            }
            switch (code) {
                case HttpsURLConnection.HTTP_UNAUTHORIZED:
                    throw new AuthorizationFailException();
                case HttpsURLConnection.HTTP_PRECON_FAILED:
                case 429: // too many requests
                    throw new ResourceLimitException();
                case HttpsURLConnection.HTTP_NOT_FOUND:
                    throw new KernelExpiredException();
                default:
                    throw new UnknownException(String.format("Error status : %d", code));
            }
        }
        return response;
    }

    private static JsonObject parseResponseAsJson(Response response) throws IOException {
        String contentType = response.header("Content-Type");
        assert contentType.startsWith("application/json") ||
               contentType.startsWith("application/problem+json");
        JsonElement je;
        JsonObject result;
        try {
            String body = response.body().string();
            je = new JsonParser().parse(body);
        } catch (JsonSyntaxException e) {
            throw new IOException("Could not parse JSON from the response body.", e);
        }
        try {
            result = je.getAsJsonObject();
        } catch (IllegalStateException e) {
            throw new IOException("Could not struct JsonObject from the parsed body.", e);
        }
        return result;
    }

    private static String parseResponseAsString(Response response) throws IOException {
        String contentType = response.header("Content-Type");
        assert contentType.startsWith("text/");
        String result = response.body().string();
        return result;
    }

    public String getId() {
        return this.sessionToken;
    }

    private String generateRunId() {
        int length = 8;
        String randomStr = UUID.randomUUID().toString();
        while (randomStr.length() < length) {
            randomStr += UUID.randomUUID().toString();
        }
        return randomStr.substring(0, length);
    }

    private String generateSessionToken() {
        String randomStr = UUID.randomUUID().toString().replaceAll("-", "");
        return randomStr;
    }
}