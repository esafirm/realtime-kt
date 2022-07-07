package nolambda.stream.realtime.internal

import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ListenableWebSocket(
    private val webSocket: WebSocket,
    private val internalListener: CompositeWebSocketListener
) : WebSocket by webSocket {

    fun listen(webSocketListener: WebSocketListener): () -> Unit {
        internalListener.listeners.add(webSocketListener)
        return {
            internalListener.listeners.remove(webSocketListener)
        }
    }

    fun clearListeners() {
        internalListener.listeners.clear()
    }

}
