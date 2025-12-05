package com.bizur.android.push

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PushRegistrationClient(
    baseUrl: String,
    json: Json,
    authToken: String
) {
    private val root = baseUrl.trimEnd('/')
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        if (authToken.isNotBlank()) {
            defaultRequest {
                header("x-api-key", authToken)
            }
        }
    }

    suspend fun register(payload: PushRegistrationPayload) {
        client.post("$root/push/register") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    fun close() {
        client.close()
    }
}

@Serializable
data class PushRegistrationPayload(
    val peerCode: String,
    val deviceId: Int,
    val token: String,
    val timestamp: Long,
    val identityKey: String,
    val signature: String
)
