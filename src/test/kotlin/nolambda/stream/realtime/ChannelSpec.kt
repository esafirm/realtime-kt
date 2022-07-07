package nolambda.stream.realtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import nolambda.stream.realtime.realtime.internal.payload.PayloadResponse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

internal class ChannelSpec : StringSpec({

    "channel should be initially closed" {
        val channel = RealtimeSubscription("topic", RealtimeClient("endpoint"))
        channel.isClosed() shouldBe true

        channel.sendJoin(5.seconds.toLong(DurationUnit.SECONDS))
        channel.isJoining() shouldBe true
    }

    "constructor should set for defaults" {
        val socket = RealtimeClient("", timeout = 1234L)
        val channel = RealtimeSubscription("topic", socket, params = mapOf("one" to "two"))

        channel.isClosed() shouldBe true
        channel.topic shouldBe "topic"
        channel.params shouldBe mapOf("one" to "two")
        channel.socket shouldBe socket
    }

    "join should be valid" {
        val socket = RealtimeClient("wss://example.com/socket")
        val channel = socket.channel("topic", chanParams = mapOf("one" to "two"))

        channel.subscribe()
        channel.isJoining() shouldBe true

        // Throw if subscribe multiple time
        shouldThrow<IllegalStateException> {
            channel.subscribe()
        }
    }

    "on error should be valid" {
        val socket = RealtimeClient("/socket")
        val channel = socket.channel("topic", chanParams = mapOf("one" to "two"))

        channel.subscribe()
        channel.isErrored() shouldBe false
        channel.trigger(ChannelEvents.ERROR.asEventName())

        channel.isErrored() shouldBe true
    }

    "on close should be valid" {
        val socket = RealtimeClient("/socket")
        val channel = socket.channel("topic", chanParams = mapOf("one" to "two"))

        channel.subscribe()
        channel.isClosed() shouldBe false
        channel.trigger(ChannelEvents.CLOSE.asEventName())

        channel.isClosed() shouldBe true
    }

    "on sets up callback for event" {
        val channel = RealtimeSubscription("topic", RealtimeClient("wss://example.com/socket"))

        var callbackCalled = 0
        channel.on("event") { _, _ ->
            callbackCalled++
        }

        channel.trigger("event")
        callbackCalled shouldBe 1
    }

    "on other feedback callbacks are ignored" {
        val channel = RealtimeSubscription("topic", RealtimeClient("wss://example.com/socket"))

        var eventCallbackCalled = 0
        var otherEventCallbackCalled = 0

        channel.on("event") { _, _ -> eventCallbackCalled++ }
        channel.on("otherEvent") { _, _ -> otherEventCallbackCalled++ }

        channel.trigger("event")

        eventCallbackCalled shouldBe 1
        otherEventCallbackCalled shouldBe 0
    }

    "on with '*` bind all events" {
        val channel = RealtimeSubscription("topic", RealtimeClient("wss://example.com/socket"))

        var callbackCalled = 0

        channel.on("*") { _, _ -> callbackCalled++ }
        channel.trigger("INSERT")
        channel.trigger("*", PayloadResponse.Content(type = "INSERT"))

        callbackCalled shouldBe 0

        channel.trigger("INSERT", PayloadResponse.Content(type = "INSERT"))
        channel.trigger("UPDATE", PayloadResponse.Content(type = "UPDATE"))
        channel.trigger("DELETE", PayloadResponse.Content(type = "DELETE"))

        callbackCalled shouldBe 3
    }

    "off removes all callbacks for event" {
        val channel = RealtimeSubscription("topic", RealtimeClient("wss://example.com/socket"))

        var callbackEventCalled1 = 0
        var callbackEventCalled2 = 0
        var callbackOtherCalled = 0

        channel.on("event") { _, _ -> callbackEventCalled1++ }
        channel.on("event") { _, _ -> callbackEventCalled2++ }
        channel.on("other") { _, _ -> callbackOtherCalled++ }

        channel.off("event")

        val defaultRef = "1"
        channel.trigger("event", ref = defaultRef)
        channel.trigger("other", ref = defaultRef)

        callbackEventCalled1 shouldBe 0
        callbackEventCalled2 shouldBe 0
        callbackOtherCalled shouldBe 1
    }

    val unsubscribeSetup = {
        val socket = RealtimeClient("wss://example.com/socket")
        val channel = socket.channel("topic", chanParams = mapOf("one" to "two"))
        channel.subscribe().trigger(PayloadStatus.OK)

        socket to channel
    }

    "unsubscribe: closes channel on `ok` from server" {
        val (socket, channel) = unsubscribeSetup()

        val anotherChannel = socket.channel("another", chanParams = mapOf("three" to "four"))
        socket.channels.size shouldBe 2

        channel.unsubscribe().trigger(PayloadStatus.OK)

        socket.channels.size shouldBe 1
        socket.channels.first() shouldBe anotherChannel
    }

    "unsubscribe: set states to closed on `ok` event" {
        val (_, channel) = unsubscribeSetup()

        channel.isClosed() shouldBe false
        channel.unsubscribe().trigger(PayloadStatus.OK)
        channel.isClosed() shouldBe true
    }

    "unsubscribe: able to unsubscribe from * subscription" {
        val (socket, channel) = unsubscribeSetup()

        channel.on("*") { _, _ -> }
        socket.channels.size shouldBe 1

        channel.unsubscribe().trigger(PayloadStatus.OK)
        socket.channels.size shouldBe 0
    }
})

