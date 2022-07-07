package nolambda.stream.realtime

enum class ChannelState {
    CLOSED,
    ERRORED,
    JOINED,
    JOINING,
    LEAVING
}

internal enum class SocketState {
    CONNECTING,
    OPEN,
    CLOSING,
    CLOSED,
    DISCONNECTED
}
