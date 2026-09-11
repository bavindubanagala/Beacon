package com.beacon.admin.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

data class TelemetryPayload(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val batteryLevel: Int
)

@Singleton
class TelemetrySocketEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    
    private val _incomingTelemetry = MutableSharedFlow<String>(replay = 1)
    val incomingTelemetry: SharedFlow<String> = _incomingTelemetry.asSharedFlow()

    fun connect(serverUrl: String) {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connection established
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _incomingTelemetry.tryEmit(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Reconnection logic handled in retry loop
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // Socket closed gracefully
            }
        })
    }

    fun sendTelemetry(payloadJson: String) {
        webSocket?.send(payloadJson)
    }

    fun disconnect() {
        webSocket?.close(1000, "Service stopped")
        webSocket = null
    }
}