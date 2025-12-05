package com.bizur.android.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import com.bizur.android.crypto.PreKeyBundlePayload
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout

class PreKeyService(
    baseUrl: String,
    json: Json
) {
    private val root = baseUrl.trimEnd('/')
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    suspend fun publish(identity: String, bundle: PreKeyBundlePayload) {
        runCatching {
            client.put("$root/prekeys/$identity") {
                contentType(ContentType.Application.Json)
                setBody(bundle)
            }
        }
    }

    suspend fun fetch(identity: String): PreKeyBundlePayload? {
        return runCatching {
            client.get("$root/prekeys/$identity").body<PreKeyBundlePayload>()
        }.getOrNull()
    }

    fun close() {
        client.close()
    }
}
