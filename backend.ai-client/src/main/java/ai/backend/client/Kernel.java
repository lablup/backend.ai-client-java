package ai.backend.client;

import ai.backend.client.exceptions.*;
import com.google.gson.*;
import okhttp3.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;



public class Kernel {
    private final Config config;
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

    private Kernel(String sessionToken, String kernelType, Config config) throws ServiceUnavaliableException, NetworkFailException, UnknownException{
        String token;
        if(sessionToken == null) {
            token = generateSessionToken();
        } else {
            token = sessionToken;
        }

        this.config = config;
        this.auth = new Auth(config);
        this.kernelType = kernelType;
        this.sessionToken = createKernel(token);
        this.runId = generateRunId();
    }

    public static Kernel getOrCreateInstance(String sessionToken, String kernelType, Config config) {
        return new Kernel(sessionToken, kernelType,  config);
    }

    public RunResult runCode(String code) throws KernelExpiredException{
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mode", "query");
        jsonObject.addProperty("code", code);
        jsonObject.addProperty("runId", this.runId);
        String requestBody = GSON.toJson(jsonObject);
        JsonObject result = null;
        try {
            result = this.request("POST", String.format("/%s/kernel/%s", this.config.getApiVersionMajor(), this.sessionToken), requestBody);
        } catch (UnknownException badRequest) {
            badRequest.printStackTrace();
        }
        return new RunResult(result);
    }

    public void destroy() throws UnknownException{
        this.request("DELETE", String.format("/%s/kernel/%s", this.config.getApiVersionMajor(), this.sessionToken), "");
    }

    public void refresh() throws UnknownException{
        this.request("PATCH", String.format("/%s/kernel/%s", this.config.getApiVersionMajor(), this.sessionToken), "");
    }

    public void interrupt() throws UnknownException{
        this.request("POST", String.format("/%s/kernel/%s/interrupt", this.config.getApiVersionMajor(), this.sessionToken), "");
    }

    private String verifyKernel() throws UnknownException, KernelExpiredException {
        JsonObject result = this.request("GET", String.format("/%s/kernel/%s", this.config.getApiVersionMajor(), this.sessionToken), "");
        if(result.has("lang")) {
            return result.get("lang").getAsString();
        } else {
            return null;
        }
    }
    /**
     * Requests to generate Kernel.
     *
     * @exception  ConfigurationException if Authorization fails.
     */
    public String createKernel(String token){
        String kernelId;
        JsonObject args = new JsonObject();
        args.addProperty("lang", this.kernelType);
        args.addProperty("clientSessionToken", token);
        JsonObject resourceLimits = new JsonObject();
        resourceLimits.addProperty("maxMem", 0);
        resourceLimits.addProperty("timeout", 0);
        args.add("resourceLimits", resourceLimits);

        String requestBody = GSON.toJson(args);

        JsonObject result = this.request("POST", String.format("/%s/kernel/create", this.config.getApiVersionMajor()), requestBody);
        if(result.has("kernelId")) {
            kernelId = (result.get("kernelId").getAsString());
        } else {
            throw new UnknownException("Malformed JSON");
        }
        return kernelId;
    }

    private JsonObject request(String method, String queryString, String requestBody) throws BaseException {
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
                .addHeader("x-backendai-version", this.config.getApiVersion())
                .addHeader("date", dateString)
                .addHeader("User-Agent", this.config.getUserAgent())
                .addHeader("Authorization", auth)
                .build();
        try {
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
            String body = response.body().string();
            JsonElement je;
            JsonObject result;

            try {
                je = new JsonParser().parse(body);
            } catch (JsonSyntaxException e) {
                throw new NetworkFailException();
            }
            try {
                result = je.getAsJsonObject();
            } catch (IllegalStateException e) {
                result = null;
            }
            return result;
        } catch (IOException e) {
            throw new UnknownException();
        }
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