package nolambda.stream.realtime.internal

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor

class Transport {

    fun connect(
        url: String,
        headers: Map<String, String>,
        listener: WebSocketListener
    ): WebSocket {

        val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            .build()

        println("URL: $url")

        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach {
                    header(it.key, it.value)
                }
            }
            .build()

        return okHttpClient.newWebSocket(request, listener)
    }
}
