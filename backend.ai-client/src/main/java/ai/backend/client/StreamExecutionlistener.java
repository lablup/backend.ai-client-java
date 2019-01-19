package ai.backend.client;

import okhttp3.OkHttpClient;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class StreamExecutionlistener extends WebSocketListener {
    private OkHttpClient client;

    public void setClient(OkHttpClient client) {
        this.client = client;
    }

    public OkHttpClient getClient() {
        return client;
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        super.onClosed(webSocket, code, reason);
        client.dispatcher().executorService().shutdown();
    }
}
