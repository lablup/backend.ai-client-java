package ai.backend.clienttester;

import ai.backend.client.ClientConfig;
import ai.backend.client.Kernel;
import ai.backend.client.StreamExecutionHandler;
import ai.backend.client.StreamExecutionlistener;
import ai.backend.client.exceptions.AuthorizationFailureException;
import ai.backend.client.exceptions.ConfigurationException;
import ai.backend.client.exceptions.NetworkFailureException;
import ai.backend.client.values.ExecutionMode;
import ai.backend.client.values.ExecutionResult;
import ai.backend.client.values.RunStatus;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;
import com.google.gson.*;

public class Main {

    private final static Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {

        Kernel kernel;

        CommandLine cmd;
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        Options options = new Options();

        options.addOption(Option.builder("k")
                .longOpt("kernel")
                .hasArg()
                .desc("Kernel type")
                .required()
                .build());
        options.addOption(Option.builder("a")
                .longOpt("accesskey")
                .hasArg()
                .desc("Backend AI accesskey")
                .required(false)
                .build());
        options.addOption(Option.builder("s")
                .longOpt("secretkey")
                .hasArg()
                .desc("Backend AI secretkey")
                .required(false)
                .build());
        options.addOption(Option.builder()
                .longOpt("encoding")
                .hasArg(true)
                .required(false)
                .desc("Encoding of the input file")
                .build());
        options.addOption(Option.builder("b")
                .longOpt("buildcmd")
                .hasArg()
                .desc("Command for build")
                .required(false)
                .build());
        options.addOption(Option.builder("e")
                .longOpt("execcmd")
                .hasArg()
                .desc("Command for build")
                .required(false)
                .build());

        options.addOption(Option.builder("d")
                .longOpt("basedirectory")
                .hasArg()
                .desc("Base")
                .required(false)
                .build());

        try {
            cmd = parser.parse(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println(e.getMessage());
            formatter.printHelp("java -jar backend.ai-client-test.jar", options);
            System.exit(1);
            return;
        }

        String[] x = cmd.getArgs();
        String basedir = cmd.getOptionValue("d", ".");

        String base = "";
        try {
            base = getBaseDirectory(basedir);
        } catch (IOException e) {
            System.err.println("Invalid target directory");
            System.exit(1);
        }
        HashMap<String, String> target_files = new HashMap<String, String>();

        for(String el : x) {
            try {
                String t = getUnixRelativePath(base, el);
                target_files.put(t, el);
            } catch (IOException e) {
                System.err.println("Invalid target file error. Check your file exists under the base directory.");
                System.exit(1);
            }
        }
        try {
            kernel = createKernel(cmd);
        } catch (ConfigurationException e) {
            System.err.println("Bad ClientConfig");
            System.err.println(e.getMessage());
            return;
        }
        LOGGER.info(String.format("Kernel is ready : %s", kernel.getId()));

        uploadFiles(kernel, target_files);

        String buildCmd = cmd.getOptionValue("b", "*");
        String execCmd = cmd.getOptionValue("e", "*");
        runStreamCode(kernel, buildCmd, execCmd);
    }

    private static void uploadFiles(Kernel kernel, HashMap<String, String> files) {
        kernel.upload(files);
    }

    private static Kernel createKernel(CommandLine cmd) throws ConfigurationException {
        String accessKey;
        String secretKey;
        String endpoint;
        Kernel kernel = null;

        if (cmd.hasOption("accesskey")) {
            accessKey = cmd.getOptionValue("accesskey");
        } else {
            accessKey = System.getenv("BACKEND_ACCESS_KEY");
        }
        if (cmd.hasOption("secretkey")) {
            secretKey = cmd.getOptionValue("secretkey");
        } else {
            secretKey = System.getenv("BACKEND_SECRET_KEY");
        }
        endpoint = System.getenv("BACKEND_ENDPOINT");

        ClientConfig.Builder builder =  new ClientConfig.Builder().accessKey(accessKey).secretKey(secretKey);
        if (endpoint != null) {
            builder.endPoint(endpoint);
        }
        ClientConfig config = builder.build();

        try {
            String sessToken = Kernel.generateSessionToken();
            kernel = Kernel.getOrCreateInstance(sessToken, cmd.getOptionValue("kernel"), config);
        } catch (AuthorizationFailureException e) {
            LOGGER.log(SEVERE,"Authorization Error");
        } catch (NetworkFailureException e) {
            LOGGER.log(SEVERE,"Network fail");
        }
        return kernel;
    }

    public static void runCode(Kernel kernel, String buildCmd, String execCmd) {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        ExecutionMode mode = ExecutionMode.BATCH;
        String runId = Kernel.generateRunId();
        String code = "";
        while (true) {
            JsonObject opts = new JsonObject();
            opts.addProperty("build", buildCmd);
            opts.addProperty("exec", execCmd);

            ExecutionResult result = kernel.execute(mode, runId, code, opts);
            System.out.print(result.getStdout());
            System.err.print(result.getStderr());
            if (result.isFinished()) {
                LOGGER.info(String.format("Finished: ", kernel.getId()));
                break;
            }
            if (result.getStatus() == RunStatus.WAITING_INPUT) {
                try {
                    code = stdin.readLine();
                } catch (IOException e) {
                    code = "<user-input error>";
                }
                mode = ExecutionMode.INPUT;
            } else {
                code = "";
                mode = ExecutionMode.CONTINUE;
            }
        }
    }

    public static void runStreamCode(Kernel kernel, String buildCmd, String execCmd) {
        ExecutionMode mode = ExecutionMode.BATCH;
        String runId = Kernel.generateRunId();
        String code = "";
        JsonObject opts = new JsonObject();
        opts.addProperty("build", buildCmd);
        opts.addProperty("exec", execCmd);

        XXListener listener = new XXListener();
        StreamExecutionHandler ws = kernel.streamExecute(mode, runId, code, opts, listener);
    }

    private static void finish(Kernel kernel) {
        kernel.destroy();
    }

    protected static String getBaseDirectory(String base) throws IOException {
        File fp = new File(base);
        if (fp.exists() && fp.isDirectory()) {
            String normalizedPath = FilenameUtils.normalize(new File(base).getAbsolutePath());
            if(!normalizedPath.endsWith(File.separator)) {
                normalizedPath = normalizedPath + File.separator;
            }
            return normalizedPath;
        }
        throw new IOException("Invalid base directory");
    }

    protected static String getUnixRelativePath(String base, String path) throws IOException {
        File f = new File(path);
        String normalizedPath = FilenameUtils.normalize(f.getAbsolutePath());
        String rp;

        if(normalizedPath.startsWith(base) && f.exists()) {
            rp = normalizedPath.substring(base.length());
        } else {
            throw new IOException("Invalid file");
        }
        rp = FilenameUtils.separatorsToUnix(rp);
        return rp;
    }

    private static final class XXListener extends StreamExecutionlistener {
        protected static Gson GSON;
        private static final int NORMAL_CLOSURE_STATUS = 1000;
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        ExecutionMode mode = ExecutionMode.BATCH;
        String code = "";

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            JsonElement je = new JsonParser().parse(String.format("{\"result\": %s }",text));
            ExecutionResult result = new ExecutionResult(je.getAsJsonObject());
            System.out.print(result.getStdout());
            System.err.print(result.getStderr());
            if (result.isFinished()) {
                webSocket.close(NORMAL_CLOSURE_STATUS, null);
            }
            if (result.getStatus() == RunStatus.WAITING_INPUT) {
                try {
                    code = stdin.readLine();
                } catch (IOException e) {
                    code = "<user-input error>";
                }
                mode = ExecutionMode.INPUT;
                this.send_code(webSocket, mode, code, null);

            }
        }

        private void send_code(WebSocket webSocket, ExecutionMode mode, String code, JsonObject opts) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("mode", mode.getValue());
            jsonObject.addProperty("code", code);
            if (opts != null) {
                jsonObject.add("options", opts);
            }
            String requestBody = GSON.toJson(jsonObject);
            webSocket.send(requestBody);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);
            LOGGER.info(String.format("Finished"));
        }
    }
}