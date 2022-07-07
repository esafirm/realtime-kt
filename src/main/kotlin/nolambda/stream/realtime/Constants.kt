package nolambda.stream.realtime

import java.util.concurrent.TimeUnit

object Constants {
    const val VERSION = "1.0.0"
    const val WS_CLOSE_NORMAL = 1000
    val defaultTimeout = TimeUnit.MINUTES.toMillis(10)
    val defaultHeaders = mapOf(
        "X-Client-Info" to "realtime-kt/$VERSION"
    )
}
