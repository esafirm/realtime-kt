package nolambda.stream.realtime

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import nolambda.stream.realtime.internal.payload.PayloadParameter
import nolambda.stream.realtime.internal.payload.PayloadResponse
import java.util.*
import kotlin.concurrent.timer

typealias PushCallback = (response: Any?) -> Unit

/**
 * Initializes the Push
 *
 * @param channel The channel
 * @param event The event, for example "phx_join"
 * @param payloadParameter the payload, for example {user_ud: 123}
 * @param timeOut push timeout in ms
 */
class Push(
    private val channel: RealtimeSubscription,
    private val event: ChannelEvents,
    private var payloadParameter: PayloadParameter = mutableMapOf(),
    private var timeOut: Long = Constants.defaultTimeout
) {

    private var ref: String? = null
    private var refEvent: String? = null
    private var receivedResp: PayloadResponse.Content? = null

    private val recHooks: MutableList<Hook> = mutableListOf()

    private var sent: Boolean = false

    private var timeOutTimer: Timer? = null

    fun resend(timeOut: Long) {
        this.timeOut = timeOut
        cancelRefEvent()
        ref = ""
        refEvent = null
        receivedResp = null
        sent = false
        send()
    }

    fun send() {
        if (hasReceived(PayloadStatus.TIME_OUT)) return

        startTimeOut()
        sent = true

        val message = Message(
            topic = channel.topic,
            payload = payloadParameter,
            event = event,
            ref = ref
        )
        channel.socket.push(message)
    }

    fun updatePayload(payload: Map<String, String>) {
        this.payloadParameter = this.payloadParameter + payload
    }

    fun receive(status: String, callback: PushCallback): Push {
        if (hasReceived(status)) {
            callback(receivedResp?.response)
        }
        recHooks.add(Hook(status, callback))
        return this
    }

    fun startTimeOut() {
        if (timeOutTimer != null) return

        ref = channel.socket.makeRef()
        val event = ref.toReplyEventName()
        refEvent = event

        channel.on(event) { payload: PayloadResponse.Content, _ ->
            cancelRefEvent()
            cancelTimeOut()

            // Update payload
            receivedResp = payload

            val status = payload.status ?: return@on
            val response = payload.response ?: emptyMap<String, String>()
            matchReceive(status, response)
        }

        timeOutTimer = timer(
            initialDelay = timeOut,
            period = timeOut
        ) {
            trigger(PayloadStatus.TIME_OUT)
        }
    }

    fun trigger(status: String, response: JsonElement = JsonObject(content = emptyMap())) {
        if (refEvent == null) return

        channel.trigger(
            event = refEvent!!,
            payload = PayloadResponse.Content(
                status = status,
                response = response
            )
        )
    }

    fun cancelRefEvent() {
        val currentRefEvent = refEvent ?: return
        channel.off(currentRefEvent)
    }

    fun cancelTimeOut() {
        timeOutTimer?.cancel()
        timeOutTimer = null
    }

    /**
     * Broadcast the new [response] to all [Hook]s that match with the [status]
     */
    private fun matchReceive(status: String, response: Any) {
        recHooks.filter { it.status == status }.forEach {
            it.callback(response)
        }
    }

    private fun hasReceived(status: String): Boolean {
        val resp = receivedResp ?: return false
        return resp.status == status
    }

    fun ref(): String = this.ref ?: ""

    class Hook(
        val status: String,
        val callback: PushCallback
    )
}
