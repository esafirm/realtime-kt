package nolambda.stream.realtime

import nolambda.stream.realtime.internal.RetryTimer
import nolambda.stream.realtime.internal.payload.PayloadParameter
import nolambda.stream.realtime.internal.payload.PayloadResponse
import java.util.concurrent.atomic.AtomicBoolean

typealias BindingCallback = (payload: PayloadResponse.Content, ref: String?) -> Unit

class RealtimeSubscription(
    internal val topic: String,
    internal val socket: RealtimeClient,
    internal val params: PayloadParameter = emptyMap(),
    private val logger: SimpleLogger = SystemOutLogger(RealtimeSubscription::class)
) {
    private var state: ChannelState = ChannelState.CLOSED

    private val pushBuffer: MutableList<Push> = mutableListOf()
    private val bindings: MutableList<Binding> = mutableListOf()

    internal val joinedOnce = AtomicBoolean(false)

    private val joinPush: Push = Push(this, ChannelEvents.JOIN, params, socket.timeout)

    private val rejoinTimer = RetryTimer { rejoinUntilConnected() }

    private val defaultTimeOut: Long = Constants.defaultTimeout

    init {
        joinPush.receive(PayloadStatus.OK) {
            state = ChannelState.JOINED
            rejoinTimer.reset()

            pushBuffer.forEach { it.send() }
            pushBuffer.clear()
        }

        onClose {
            rejoinTimer.reset()
            logger.log { "Channel: close $topic ${joinRef()}" }

            state = ChannelState.CLOSED
            socket.remove(this)
        }

        onError { reason ->
            if (isLeaving() || isClosed()) {
                return@onError
            }
            logger.log { "Channel: error $topic - $reason" }
            state = ChannelState.ERRORED
            rejoinTimer.scheduleTimeout()
        }

        joinPush.receive(PayloadStatus.TIME_OUT) {
            if (!isJoining()) return@receive
            logger.log { "Channel: timeout. Topic:$topic" }

            state = ChannelState.ERRORED
            rejoinTimer.scheduleTimeout()
        }

        on(ChannelEvents.REPLY.asEventName()) { payload, ref ->
            trigger(ref.toReplyEventName(), payload)
        }
    }

    private fun rejoinUntilConnected() {
        rejoinTimer.scheduleTimeout()
        if (socket.isConnected()) {
            rejoin()
        }
    }

    fun subscribe(timeOut: Long = defaultTimeOut): Push {
        if (joinedOnce.compareAndSet(false, true)) {
            rejoin(timeOut)
            return joinPush
        }
        error("tried to subscribe multiple times. 'subscribe' can only be called a single time per channel instance")
    }

    fun onClose(callback: () -> Unit) {
        on(ChannelEvents.CLOSE.asEventName()) { reason, _ ->
            println("Reason: $reason")
            callback()
        }
    }

    fun onError(callback: (reason: String?) -> Unit) {
        on(ChannelEvents.ERROR.asEventName()) { reason: Any?, _ ->
            callback(reason.toString())
        }
    }

    @Synchronized
    fun on(
        event: String,
        callback: BindingCallback
    ) {
        bindings.add(Binding(event, callback))
    }

    @Synchronized
    fun off(eventName: String) {
        bindings.removeAll { it.event == eventName }
    }

    fun canPush(): Boolean {
        return socket.isConnected() && isJoined()
    }

    fun push(
        event: ChannelEvents,
        payload: Map<String, String>,
        timeOut: Long? = null
    ): Push {
        if (!joinedOnce.get()) {
            error("tried to push '$event' to '$topic' before joining. Use channel.subscribe() before pushing events")
        }
        val pushEvent = Push(this, event, payload, timeOut ?: defaultTimeOut)
        if (canPush()) {
            pushEvent.send()
        } else {
            pushEvent.startTimeOut()
            pushBuffer.add(pushEvent)
        }
        return pushEvent
    }

    fun updateJoinPayload(payload: Map<String, String>) {
        joinPush.updatePayload(payload)
    }

    /**
     * Leaves the channel
     *
     * Unsubscribes from server events, and instructs channel to terminate on server.
     * Triggers onClose() hooks.
     *
     * To receive leave acknowledgements, use the a `receive` hook to bind to the server ack,
     * ```kotlin
     * channel.unsubscribe().receive("ok") { println("left") }
     * ```
     */
    fun unsubscribe(timeOut: Long = defaultTimeOut): Push {
        fun onClose() {
            logger.log { "Channel: Leave $topic" }
            trigger(
                ChannelEvents.CLOSE.asEventName(),
                payload = PayloadResponse.Content("", type = "leave"),
                ref = joinRef()
            )
        }

        state = ChannelState.LEAVING

        val leavePush = Push(this, ChannelEvents.LEAVE, timeOut = timeOut)
        leavePush.receive(PayloadStatus.OK) {
            onClose()
        }.receive(PayloadStatus.TIME_OUT) {
            onClose()
        }
        leavePush.send()

        if (!canPush()) {
            leavePush.trigger(PayloadStatus.OK)
        }

        return leavePush
    }

    fun isMember(passedTopic: String) = topic == passedTopic

    fun joinRef() = joinPush.ref()

    fun sendJoin(timeOut: Long) {
        state = ChannelState.JOINING
        joinPush.resend(timeOut)
    }

    fun rejoin(timeOut: Long = defaultTimeOut) {
        if (isLeaving()) return
        sendJoin(timeOut)
    }

    /**
     * Broadcast [event] and [payload] to matching [Binding]
     */
    fun trigger(
        event: String,
        payload: PayloadResponse.Content = PayloadResponse.Content.EMPTY,
        ref: String? = null
    ) {
        val events = listOf(
            ChannelEvents.CLOSE, ChannelEvents.ERROR, ChannelEvents.LEAVE, ChannelEvents.JOIN
        ).map { it.asEventName() }.toSet()

        if (ref != null && events.contains(event) && ref != joinRef()) {
            // Still don't know what this is about
            return
        }

        // TODO: handle onMessage override

        val filteredBindings = bindings.filter { bind ->
            if (bind.event == "*") {
                event == payload.type.orEmpty()
            } else {
                bind.event == event
            }
        }

        filteredBindings.forEach {
            it.callback(payload, ref)
        }
    }

    /* --------------------------------------------------- */
    /* > State Getter */
    /* --------------------------------------------------- */

    fun isClosed(): Boolean = state == ChannelState.CLOSED

    fun isErrored(): Boolean = state == ChannelState.ERRORED

    fun isJoined(): Boolean = state == ChannelState.JOINED

    fun isJoining(): Boolean = state == ChannelState.JOINING

    fun isLeaving(): Boolean = state == ChannelState.LEAVING

    class Binding(
        val event: String,
        val callback: BindingCallback
    )
}
