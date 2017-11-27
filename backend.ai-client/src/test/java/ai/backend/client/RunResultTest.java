package ai.backend.client;

import ai.backend.client.values.RunStatus;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RunResultTest {
    @Test
    public void ContRunResultTest() {

        String contString = "{\"result\":{\"status\":\"continued\",\"console\":[],\"files\":[]}}";
        JsonElement je = new JsonParser().parse(contString);

        RunResult runResult = new RunResult(je.getAsJsonObject());
        assertEquals(runResult.getAsJson(), contString);
        assertEquals(runResult.getStatus(), RunStatus.CONTINUED);
    }
    @Test
    public void FinRunResultTest() {

        String contString = "{\"result\":{\"status\":\"finished\",\"console\":[],\"files\":[]}}";
        JsonElement je = new JsonParser().parse(contString);

        RunResult runResult = new RunResult(je.getAsJsonObject());
        assertEquals(runResult.getAsJson(), contString);
        assertEquals(runResult.getStatus(), RunStatus.FINISHED);
    }
}