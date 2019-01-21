package ai.backend.client;

import okhttp3.WebSocket;

public class StreamExecutionHandler{
    private WebSocket ws;

    public StreamExecutionHandler(WebSocket ws) {
        this.ws = ws;
    }

    void send(String str) {
        ws.send(str);
    }
    void send(String code, String mode, String options) {
    }

}
