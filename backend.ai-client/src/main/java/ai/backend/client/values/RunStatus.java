package ai.backend.client.values;

import java.util.HashMap;
import java.util.Map;

public enum RunStatus {
    FINISHED("finished"),
    WAITING_INPUT("waiting-input"),
    CONTINUED("continued"),
    UNKNOWN("");

    private String statusStr;

    RunStatus(String statusStr) {
        this.statusStr = statusStr;
    }

    private static final Map<String, RunStatus> lookup = new HashMap<String, RunStatus>();

    static {
        for (RunStatus r : RunStatus.values()) {
            lookup.put(r.getValue(), r);
        }
    }

    public String getValue() {
        return statusStr;
    }

    public static RunStatus get(String statusStr) {
        return lookup.get(statusStr);
    }
}
