package ai.backend.clienttester;

import ai.backend.client.ClientConfig;
import ai.backend.client.Kernel;
import ai.backend.client.values.ExecutionMode;
import ai.backend.client.values.ExecutionResult;
import ai.backend.client.exceptions.AuthorizationFailException;
import ai.backend.client.exceptions.ConfigurationException;
import ai.backend.client.exceptions.NetworkFailException;
import ai.backend.client.values.RunStatus;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

public class Main {

    private final static Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        Options options = new Options();

        Option inputFileOpt = new Option("f", "file", true, "Input file path");
        inputFileOpt.setRequired(true);
        options.addOption(inputFileOpt);

        Option kernelTypeOpt = new Option("k", "kernel", true, "Kernel type");
        kernelTypeOpt.setRequired(true);
        options.addOption(kernelTypeOpt);

        Option accessKeyOpt = new Option("a", "accesskey", true, "Backend AI accesskey");
        accessKeyOpt.setRequired(false);
        options.addOption(accessKeyOpt);

        Option secretKeyOpt = new Option("s", "secretkey", true, "Backend AI secretkey");
        secretKeyOpt.setRequired(false);
        options.addOption(secretKeyOpt);

        Option encodingOpt = new Option("e", "encoding", true, "Encoding of the input file");
        encodingOpt.setRequired(false);
        options.addOption(encodingOpt);

        try {
            cmd = parser.parse(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);

            System.exit(1);
            return;
        }

        Kernel kernel;
        String code;

        try {
            code = readFile(cmd.getOptionValue("file"), Charset.forName(cmd.getOptionValue("encoding", "UTF-8")));
        } catch (IOException e) {
            LOGGER.log(SEVERE,"ERROR: Can't open the file");
            return;
        } catch (UnsupportedCharsetException e) {
            LOGGER.log(SEVERE, "ERROR: Bad encoding");
            return;
        }

        try {
            kernel = createKernel(cmd);
        } catch (ConfigurationException e) {
            System.err.println("Bad ClientConfig");
            return;
        }
        runCode(kernel, code);
        finish(kernel);
    }

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        FileInputStream stream = new FileInputStream(path);
        try {
            Reader reader = new BufferedReader(new InputStreamReader(stream, encoding));
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } finally {
            stream.close();
        }
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
            kernel = Kernel.getOrCreateInstance(null, cmd.getOptionValue("kernel"), config);
        } catch (AuthorizationFailException e) {
            LOGGER.log(SEVERE,"Authorization Error");
        } catch (NetworkFailException e) {
            LOGGER.log(SEVERE,"Network fail");
        }
        return kernel;
    }

    public static void runCode(Kernel kernel, String code) {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        LOGGER.info(String.format("Kernel is ready : %s", kernel.getId()));
        ExecutionMode mode = ExecutionMode.QUERY;
        while (true) {
            ExecutionResult result = kernel.execute(mode, code, null);
            System.out.print(result.getStdout());
            System.err.print(result.getStderr());
            if (result.isFinished()) {
                break;
            }
            if (result.getStatus() == RunStatus.WAITING_INPUT) {
                try {
                    code = stdin.readLine();
                } catch (IOException e) {
                    code = "";
                }
                mode = ExecutionMode.INPUT;
            } else {
                code = "";
                mode = ExecutionMode.CONTINUE;
            }
        }
    }

    private static void finish(Kernel kernel) {
        kernel.destroy();
    }
}