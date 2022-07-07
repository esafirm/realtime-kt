package nolambda.stream.realtime.internal.payload

import kotlinx.serialization.json.JsonElement

@kotlinx.serialization.Serializable
data class PayloadResponse(
    val topic: String,
    val event: String,
    val payload: Content,
    val ref: String?
) {

    @kotlinx.serialization.Serializable
    data class Content(
        val status: String? = null,
        val response: JsonElement? = null,

        // Based on dart, there's a payload type check. So we add this for now
        val type: String? = null
    ) {
        companion object {
            val EMPTY = Content()
        }
    }
}
