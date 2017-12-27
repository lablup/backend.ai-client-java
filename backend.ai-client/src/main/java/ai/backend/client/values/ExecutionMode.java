package ai.backend.client.values;

public enum ExecutionMode {
    QUERY("query"),
    BATCH("batch"),
    INPUT("input"),
    CONTINUE("continue");

    private String mode;

    ExecutionMode(String mode) {
        this.mode = mode;
    }

    public String getValue() {
        return this.mode;
    }
}
