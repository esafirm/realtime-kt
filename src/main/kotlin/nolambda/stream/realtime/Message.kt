package nolambda.stream.realtime

@kotlinx.serialization.Serializable
internal data class Message(
    val topic: String,
    val event: ChannelEvents,
    val payload: Map<String, String?>,
    val ref: String?
)
