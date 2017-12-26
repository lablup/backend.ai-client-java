package ai.backend.client.values;

import ai.backend.client.values.RunStatus;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ExecutionResult {
    private String stderr = "";
    private JsonObject jsonResult;
    private RunStatus status;
    private String stdout = "";

    public ExecutionResult(JsonObject jsonResult) {
        this.jsonResult = jsonResult;
        try {
            JsonObject result = jsonResult.get("result").getAsJsonObject();
            JsonArray console = result.get("console").getAsJsonArray();
            this.status = RunStatus.get(result.get("status").getAsString());
            for (int i = 0; i < console.size(); i++) {
                JsonArray a = console.get(i).getAsJsonArray();
                String type = a.get(0).getAsString();
                if(type.equals("stdout")) {
                    this.stdout = a.get(1).getAsString();
                } else if (type.equals("stderr")) {
                    this.stderr = a.get(1).getAsString();
                }
            }
        } catch (NullPointerException e) {
        }
    }

    public String getAsJson() {
        Gson gson = new Gson();
        String result = gson.toJson(jsonResult);
        return result;
    }

    public RunStatus getStatus() {
        return status;
    }
    public String getStdout() {
        return stdout;
    }
    public String getStderr() {
        return stderr;
    }

    public boolean isFinished() {
        return status == RunStatus.FINISHED;
    }
    public boolean isContinued() { return status == RunStatus.CONTINUED; }

}