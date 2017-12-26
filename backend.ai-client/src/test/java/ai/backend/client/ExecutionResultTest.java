package ai.backend.client;

import ai.backend.client.values.ExecutionResult;
import ai.backend.client.values.RunStatus;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExecutionResultTest {
    @Test
    public void ContinuedExecutionTest() {

        String contString = "{\"result\":{\"status\":\"continued\",\"console\":[],\"files\":[]}}";
        JsonElement je = new JsonParser().parse(contString);

        ExecutionResult result = new ExecutionResult(je.getAsJsonObject());
        assertEquals(result.getAsJson(), contString);
        assertEquals(result.getStatus(), RunStatus.CONTINUED);
    }

    @Test
    public void FinishedExecutionTest() {

        String contString = "{\"result\":{\"status\":\"finished\",\"console\":[],\"files\":[]}}";
        JsonElement je = new JsonParser().parse(contString);

        ExecutionResult result = new ExecutionResult(je.getAsJsonObject());
        assertEquals(result.getAsJson(), contString);
        assertEquals(result.getStatus(), RunStatus.FINISHED);
    }
}