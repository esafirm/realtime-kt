package nolambda.stream.realtime.internal

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class CompositeWebSocketListener(
    val listeners: MutableSet<WebSocketListener> = mutableSetOf()
) : WebSocketListener() {

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        listeners.forEach { it.onClosed(webSocket, code, reason) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        listeners.forEach { it.onClosing(webSocket, code, reason) }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        listeners.forEach { it.onFailure(webSocket, t, response) }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        listeners.forEach { it.onMessage(webSocket, text) }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        listeners.forEach { it.onMessage(webSocket, bytes) }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        listeners.forEach { it.onOpen(webSocket, response) }
    }
}
