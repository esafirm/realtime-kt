package nolambda.stream.realtime

/**
 * Convert string to reply event name
 */
fun String?.toReplyEventName(): String {
    return "chan_reply_$this"
}
