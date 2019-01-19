package ai.backend.client;

import ai.backend.client.exceptions.*;
import ai.backend.client.values.ExecutionMode;
import ai.backend.client.values.ExecutionResult;
import com.google.gson.JsonObject;
import okhttp3.*;
import okio.BufferedSink;
import okio.ByteString;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static okhttp3.MultipartBody.FORM;


public class Kernel extends APIFunction {
    private final String kernelType;
    private final String sessionToken;

    private Kernel(String sessionToken, String kernelType, ClientConfig config)
            throws ServiceUnavaliableException, NetworkFailureException, UnknownException {
        super(config);
        String token;
        if(sessionToken == null) {
            token = generateSessionToken();
        } else {
            token = sessionToken;
        }
        this.kernelType = kernelType;
        this.sessionToken = createKernelIfNotExists(token);
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
    public ExecutionResult execute(ExecutionMode mode, String runId, String code, JsonObject opts) throws BackendClientException {
        if (runId.length() < 8 || runId.length() > 64) {
            throw new InvalidParametersException("runId is too short or too long.");
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mode", mode.getValue());
        jsonObject.addProperty("code", code);
        if (opts != null) {
            jsonObject.add("options", opts);
        }
        jsonObject.addProperty("runId", runId);
        String makeRequestBody = GSON.toJson(jsonObject);
        try {
            Response resp = this.makeRequest("POST", String.format("/kernel/%s", this.sessionToken), makeRequestBody);
            JsonObject result = parseResponseAsJson(resp);
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
            this.makeRequest("DELETE", String.format("/kernel/%s", this.sessionToken));
            // TODO: support returned statistics
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
            this.makeRequest("PATCH", String.format("/kernel/%s", this.sessionToken));
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
            this.makeRequest("POST", String.format("/kernel/%s/interrupt", this.sessionToken));
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }
    }

    /**
     * Verify the kernel type.
     *
     * @return Kernel type
     * @throws BackendClientException
     */
    public String verifyType() throws BackendClientException {
        try {
            Response resp = this.makeRequest("GET", String.format("/kernel/%s", this.sessionToken));
            JsonObject result = parseResponseAsJson(resp);
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
        try {
            Response resp = this.makeRequest("POST", "/kernel/create", args);
            JsonObject result = parseResponseAsJson(resp);
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

    public void upload(Map<String, String> filePaths) {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(FORM);

        for(String name : filePaths.keySet()) {
            File file = new File(filePaths.get(name));
            builder.addFormDataPart("src", name,
                            RequestBody.create(MediaType.parse("text/plain"), file));

        }
        RequestBody requestBody = builder.build();
        try {
            this.makeRequest("POST", String.format("/kernel/%s/upload", sessionToken), requestBody, "");
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }

    }
    /**
     * Returns the session token/ID set when creating.
     */
    public String getId() {
        return this.sessionToken;
    }

    /**
     * Returns the kernel type set when creating.
     */
    public String getKernelType() {
        return this.kernelType;
    }

    /**
     * A helper method to generate a run ID.
     */
    public static String generateRunId() {
        return UUID.randomUUID().toString();
    }

    /**
     * A helper method to generate a session ID.
     */
    public static String generateSessionToken() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    public StreamExecutionHandler streamExecute(ExecutionMode mode, String runId, String code, JsonObject opts, StreamExecutionlistener listener) throws BackendClientException {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mode", mode.getValue());
        jsonObject.addProperty("code", code);
        if (opts != null) {
            jsonObject.add("options", opts);
        }
        jsonObject.addProperty("runId", runId);
        String requestBody = GSON.toJson(jsonObject);

        WebSocket ws;
        StreamExecutionHandler handler;
        try {
            RequestBody x = RequestBody.create(MediaType.parse("application/json"), new byte[0]);

            Request request = getRequest("GET", String.format("/stream/kernel/%s/execute", this.sessionToken), x, "");
            ws = this.restClient.newWebSocket(request, listener);
            listener.setClient(this.restClient);
            ws.send(requestBody);
            handler = new StreamExecutionHandler(ws);
            //this.restClient.dispatcher().executorService().shutdown();
        } catch (IOException e) {
            throw new BackendClientException("Request/response failed", e);
        }
        return handler;
    }

}