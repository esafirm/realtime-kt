package nolambda.stream.realtime

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
enum class ChannelEvents {
    @SerialName("phx_close")
    CLOSE,

    @SerialName("phx_error")
    ERROR,

    @SerialName("phx_join")
    JOIN,

    @SerialName("phx_reply")
    REPLY,

    @SerialName("phx_leave")
    LEAVE,

    @SerialName("phx_heartbeat")
    HEART_BEAT,

    @SerialName("access_token")
    ACCESS_TOKEN
}

fun ChannelEvents.asEventName(): String {
    return when (this) {
        ChannelEvents.CLOSE -> "phx_close"
        ChannelEvents.ERROR -> "phx_error"
        ChannelEvents.JOIN -> "phx_join"
        ChannelEvents.REPLY -> "phx_reply"
        ChannelEvents.LEAVE -> "phx_leave"
        ChannelEvents.HEART_BEAT -> "phx_heartbeat"
        ChannelEvents.ACCESS_TOKEN -> "access_token"
    }
}
