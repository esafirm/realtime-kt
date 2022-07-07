## Realtime KT

Client for realtime functionality of Supabase.

The design is mainly follow [realtime-dart](https://github.com/supabase-community/realtime-dart) with few modifications.

## Example

```kotlin
val apiKey = "key"

val socket = RealtimeClient(
    endpoint = "wss://example.supabase.co/realtime/v1/websocket",
    params = mapOf(
        "apikey" to apiKey,
        "vsn" to "1.0.0"
    ),
    logger = SystemOutLogger(RealtimeClient::class)
)

val chanParams = mapOf("user_token" to apiKey)
val channel = socket.channel("realtime:public:trigger", chanParams = chanParams)

channel.on("DELETE") { payload, ref ->
    println("Channel delete payload $payload - $ref")
}
channel.on("INSERT") { payload, ref ->
    println("Channel insert payload $payload - $ref")
}

socket.onMessage(StateChangeCallback.Param { message -> println("Message: $message") })
socket.connect()

channel.subscribe().receive(PayloadStatus.OK) {
    println("SUBSCRIBED")
}
```

## License

MIT @ Esa Firman
