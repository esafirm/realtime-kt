package nolambda.stream.realtime

import nolambda.stream.realtime.internal.payload.JsonPayloadMapper
import nolambda.stream.realtime.internal.EndpointMaker
import nolambda.stream.realtime.internal.payload.PayloadParameter
import nolambda.stream.realtime.internal.payload.PayloadMapper
import nolambda.stream.realtime.internal.RefManager
import nolambda.stream.realtime.internal.RetryTimer
import nolambda.stream.realtime.internal.Transport
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.*
import kotlin.concurrent.fixedRateTimer

sealed class StateChangeCallback {
    class Simple(block: () -> Unit) : () -> Unit by block, StateChangeCallback()
    class Param(block: (Any) -> Unit) : (Any) -> Unit by block, StateChangeCallback()
}

class RealtimeClient(
    private val endpoint: String,
    private val headers: Map<String, String> = emptyMap(),
    val params: Map<String, String> = emptyMap(),
    val timeout: Long = Constants.defaultTimeout,
    private val transport: Transport = Transport(),
    private val heartBeatIntervalMs: Long = 30_000,
    private val payloadMapper: PayloadMapper = JsonPayloadMapper(),
    private val logger: SimpleLogger = SimpleLogger.NOOP
) {
    val channels: MutableSet<RealtimeSubscription> = mutableSetOf()

    private var heartBeatTimer: Timer? = null

    private var socket: WebSocket? = null
    private var connectionState: SocketState? = null

    private var pendingHeartBeatRef: String? = null
    private var accessToken: String? = null

    private val refManager = RefManager()

    private val sendBuffer: MutableList<() -> Unit> = mutableListOf()

    private val statChangeCallbacks: Map<String, MutableList<StateChangeCallback>> = mapOf(
        "open" to mutableListOf(),
        "close" to mutableListOf(),
        "error" to mutableListOf(),
        "message" to mutableListOf(),
    )

    private val reconnectionTimer = RetryTimer {
        disconnect()
    }

    private val socketAddress by lazy { EndpointMaker(endpoint, params).make() }

    fun connect() {
        if (socket != null) return

        try {
            connectionState = SocketState.CONNECTING
            socket = transport.connect(socketAddress, headers, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onConnectionOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // handling of the incoming messages
                    onConnectionMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // error handling
                    onConnectionError(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    // communication has been closed
                    if (connectionState != SocketState.DISCONNECTED) {
                        connectionState = SocketState.CLOSED
                    }
                    onConnectionClose("")
                }
            })
        } catch (e: Exception) {
            onConnectionError(e)
        }
    }

    /**
     * Disconnect the socket with status [code] and [reason] for the disconnect
     */
    fun disconnect(code: Int? = null, reason: String? = null) {
        socket?.close(code ?: Constants.WS_CLOSE_NORMAL, reason)
        socket = null
    }

    /**
     * Registers callbacks for connection state change events
     *
     * Example:
     * ```
     * socket.onOpen { println("Socket opened") }
     * ```
     */
    fun onOpen(callback: StateChangeCallback.Simple) {
        statChangeCallbacks["open"]!!.add(callback)
    }

    fun onClose(callback: StateChangeCallback.Param) {
        statChangeCallbacks["close"]!!.add(callback)
    }

    fun onError(callback: StateChangeCallback.Param) {
        statChangeCallbacks["error"]!!.add(callback)
    }

    fun onMessage(callback: StateChangeCallback.Param) {
        statChangeCallbacks["message"]!!.add(callback)
    }

    // TODO: improve this
    fun connectionState(): String {
        return when (connectionState) {
            SocketState.CONNECTING -> "connecting"
            SocketState.OPEN -> "open"
            SocketState.CLOSING -> "closing"
            SocketState.CLOSED -> "closed"
            SocketState.DISCONNECTED -> "disconnected"
            null -> "closed"
        }
    }

    /**
     * Returns `true` is the connection is open.
     */
    fun isConnected(): Boolean {
        return connectionState == SocketState.OPEN
    }

    /**
     * Removes a subscription from the socket.
     */
    fun remove(channel: RealtimeSubscription) {
        channels.removeAll { it.joinRef() == channel.joinRef() }
    }

    fun channel(
        topic: String,
        chanParams: PayloadParameter = emptyMap()
    ): RealtimeSubscription {
        val chan = RealtimeSubscription(
            topic = topic,
            socket = this,
            params = chanParams
        )
        channels.add(chan)
        return chan
    }

    /**
     * Push [message]
     */
    internal fun push(message: Message) {
        fun callback() {
            payloadMapper.encode(message) {
                logger.log { "Send message $it" }
                socket?.send(it)
            }
        }

        logger.log { "Push: ${message.topic} : ${message.event} : ${message.ref}" }

        if (isConnected()) {
            callback()
        } else {
            sendBuffer.add(::callback)
        }
    }

    private fun onConnectionMessage(rawMessage: String) {
        payloadMapper.decode(rawMessage) { msg ->

            // TODO: finds out what's this
            if (msg.ref != null && msg.ref == pendingHeartBeatRef) {
                pendingHeartBeatRef = null
            }

            logger.log { "Receive: ${msg.payload}" }

            channels.filter { it.isMember(msg.topic) }.forEach {
                it.trigger(msg.event, msg.payload, msg.ref)
            }

            statChangeCallbacks["message"]?.forEach {
                if (it is StateChangeCallback.Param) {
                    it.invoke(msg)
                }
            }
        }
    }

    private fun sendHeartBeat() {
        if (!isConnected()) return

        if (pendingHeartBeatRef != null) {
            pendingHeartBeatRef = null
            logger.log { "Transport: heartbeat timeout. Attempting to re-establish connection" }
            socket?.close(Constants.WS_CLOSE_NORMAL, "heartbeat timeout")
        }

        pendingHeartBeatRef = refManager.makeRef()

        val heartBeatMessage = Message(
            topic = "phoenix",
            ChannelEvents.HEART_BEAT,
            payload = emptyMap(),
            ref = pendingHeartBeatRef
        )
        push(heartBeatMessage)
        setAuth(accessToken ?: "")
    }

    /**
     * Sets the JWT access token used for channel subscription authorization and Realtime RLS.
     * @param token - A JWT strings
     */
    fun setAuth(token: String = "") {
        accessToken = token

        channels.forEach { chan ->
            if (token.isNotEmpty()) {
                chan.updateJoinPayload(
                    mapOf(
                        "user_token" to token
                    )
                )
            }
            if (chan.joinedOnce.get() && chan.isJoined()) {
                chan.push(
                    ChannelEvents.ACCESS_TOKEN, mapOf(
                        "access_token" to token
                    )
                )
            }
        }
    }


    fun makeRef(): String = refManager.makeRef()

    /* --------------------------------------------------- */
    /* > Private */
    /* --------------------------------------------------- */

    private fun onConnectionOpen() {
        logger.log { "Transport: connected to $socketAddress" }
        connectionState = SocketState.OPEN

        flushSendBuffer()
        reconnectionTimer.reset()
        heartBeatTimer?.cancel()

        heartBeatTimer = fixedRateTimer(
            initialDelay = heartBeatIntervalMs,
            period = heartBeatIntervalMs
        ) {
            sendHeartBeat()
        }

        statChangeCallbacks["open"]?.forEach {
            if (it is StateChangeCallback.Simple) {
                it.invoke()
            }
        }
    }

    private fun onConnectionClose(event: String) {
        logger.log { "Transport: close $event" }

        // SocketStates.disconnected: by user with socket.disconnect()
        // SocketStates.closed: NOT by user, should try to reconnect
        if (connectionState == SocketState.CLOSED) {
            triggerChanError()
            reconnectionTimer.scheduleTimeout()
        }
        heartBeatTimer?.cancel()

        statChangeCallbacks["close"]?.forEach {
            if (it is StateChangeCallback.Param) {
                it.invoke(event)
            }
        }
    }

    private fun onConnectionError(t: Throwable) {
        logger.log { "Transport: connection error $t" }
        t.printStackTrace()

        triggerChanError()

        statChangeCallbacks["error"]?.forEach {
            if (it is StateChangeCallback.Param) {
                it.invoke(t)
            }
        }
    }

    private fun triggerChanError() {
        channels.forEach {
            it.trigger(ChannelEvents.ERROR.asEventName())
        }
    }

    @Synchronized
    private fun flushSendBuffer() {
        if (isConnected() && sendBuffer.isNotEmpty()) {
            sendBuffer.forEach {
                it.invoke()
            }
            sendBuffer.clear()
        }
    }
}
