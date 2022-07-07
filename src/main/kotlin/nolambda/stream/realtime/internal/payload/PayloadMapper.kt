package nolambda.stream.realtime.internal.payload

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nolambda.stream.realtime.Message
import nolambda.stream.realtime.SimpleLogger
import nolambda.stream.realtime.SystemOutLogger

typealias PayloadParameter = Map<String, String?>

interface PayloadMapper {
    fun encode(payload: Any, callback: (String) -> Unit)
    fun decode(payload: String, callback: (PayloadResponse) -> Unit)
}

class JsonPayloadMapper(
    private val logger: SimpleLogger = SystemOutLogger(JsonPayloadMapper::class)
) : PayloadMapper {

    private val json = Json { prettyPrint = true }

    override fun encode(payload: Any, callback: (String) -> Unit) {
        logger.log { "Encoding $payload" }

        val encoded = if (payload is Message) {
            json.encodeToString(payload)
        } else error("Unhandled payload type")

        callback.invoke(encoded)
    }

    override fun decode(payload: String, callback: (PayloadResponse) -> Unit) {
        logger.log { "Decoding: $payload" }
        callback.invoke(json.decodeFromString(payload))
    }
}
