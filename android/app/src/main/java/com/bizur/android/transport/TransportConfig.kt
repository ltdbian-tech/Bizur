package com.bizur.android.transport

data class TransportConfig(
    val signalingUrl: String = com.bizur.android.BuildConfig.SIGNALING_URL,
    val preKeyServiceUrl: String = com.bizur.android.BuildConfig.PREKEY_SERVICE_URL,
    val pushServiceUrl: String = com.bizur.android.BuildConfig.PUSH_SERVICE_URL,
    val apiKey: String = com.bizur.android.BuildConfig.API_KEY,
    val authToken: String = com.bizur.android.BuildConfig.AUTH_TOKEN,
    val stunServers: List<String> = listOf("stun:stun.l.google.com:19302"),
    val turnServers: List<TurnServer> = emptyList(),
    val queuePollIntervalMillis: Long = 30_000L,
    val reconnectDelayMillis: Long = 3_000L,
    val maxReconnectDelayMillis: Long = 60_000L
)

data class TurnServer(
    val urls: List<String>,
    val username: String,
    val credential: String
)
