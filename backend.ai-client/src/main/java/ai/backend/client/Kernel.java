package ai.backend.client;

import ai.backend.client.exceptions.*;
import com.google.gson.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;


public class Kernel {
    private final Config config;
    private final String kernelType;
    private final String sessionToken;
    private String runId;
    private final Auth auth;
    private static SimpleDateFormat DATEFORMAT;
    private static Gson GSON;

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
        this.sessionToken = token;
        createKernel(token);
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

    private JsonObject request(String method, String queryString, String requestBody) throws UnknownException, ServiceUnavaliableException, IllegalStateException {
        Date now = new Date();
        String dateString = String.format("%s%s", this.DATEFORMAT.format(now), "+00:00");

        HttpsURLConnection conn;
        URL url = null;
        OutputStream outputStream = null;
        int response_code;

        HashMap<String, String> headers = new HashMap<String, String>();

        String sig = this.auth.getCredentialString(method, queryString, now, requestBody);
        String auth = String.format("Sorna signMethod=HMAC-SHA256, credential=%s" ,sig);

        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Content-Length", String.format("%d", requestBody.length()));
        headers.put("X-Sorna-Version", this.config.getApiVersion());
        headers.put("X-Sorna-Date", dateString);
        headers.put("User-Agent", this.config.getUserAgent());
        headers.put("Authorization", auth);

        try {
            url = new URL(String.format("%s%s", this.config.getEndPoint(), queryString));
        } catch (MalformedURLException e) {
            throw new UnknownException();
        }

        try {
             conn = (HttpsURLConnection) url.openConnection();
        } catch (IOException e) {
            throw new NetworkFailException();
        }

        for(Map.Entry<String, String> entry : headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }

        try {
            conn.setRequestMethod(method);
        } catch (ProtocolException e) {
            throw new UnknownException("Bad Protocol");
        }

        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        try {
            outputStream = conn.getOutputStream();
            outputStream.write( requestBody.getBytes("UTF-8") );
            outputStream.close();
            response_code = conn.getResponseCode();
        } catch (UnsupportedEncodingException e) {
            throw new UnknownException();
        } catch (IOException e) {
            e.printStackTrace();
            throw new NetworkFailException();
        }

        if(response_code > HttpsURLConnection.HTTP_INTERNAL_ERROR){
            throw new ServiceUnavaliableException();
        } else if(response_code == HttpsURLConnection.HTTP_UNAUTHORIZED) {
            throw new AuthorizationFailException();
        } else if (response_code == HttpURLConnection.HTTP_NOT_FOUND) {
            throw new KernelExpiredException();
        } else if(response_code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            InputStream is = (conn.getErrorStream());
            StringBuffer buffer = new StringBuffer();
            byte[] b = new byte[4096];
            int i;

            try {
                while ((i = is.read(b)) != -1) {
                    buffer.append(new String(b, 0, i));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            String str = buffer.toString();
            System.out.println(str);
            throw new UnknownException(String.format("Error status : %d", response_code));
        }
        InputStream in;
        try {
            in = conn.getInputStream();
        } catch (IOException e) {
            throw new UnknownException();
        }
        InputStreamReader isr = new InputStreamReader(in);
        JsonElement je;
        JsonObject result;
        try {
            je = new JsonParser().parse(isr);
        } catch (JsonSyntaxException e) {
            throw new NetworkFailException();
        }
        try {
            result = je.getAsJsonObject();
        } catch (IllegalStateException e) {
            result = null;
        }
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