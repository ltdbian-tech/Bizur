package com.bizur.android.call

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSignalTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `encode and decode invite signal`() {
        val signal = CallSignal(CallSignalType.INVITE, callId = "call-123", displayName = "Alice")
        val encoded = json.encodeToString(CallSignal.serializer(), signal)
        assertTrue(encoded.contains("\"invite\""))
        val decoded = json.decodeFromString(CallSignal.serializer(), encoded)
        assertEquals(signal, decoded)
    }

    @Test
    fun `encode and decode end signal`() {
        val signal = CallSignal(CallSignalType.END, callId = "call-xyz")
        val encoded = json.encodeToString(CallSignal.serializer(), signal)
        val decoded = json.decodeFromString(CallSignal.serializer(), encoded)
        assertEquals(CallSignalType.END, decoded.type)
        assertEquals("call-xyz", decoded.callId)
    }

    @Test
    fun `encode and decode accept signal without display name`() {
        val signal = CallSignal(CallSignalType.ACCEPT, callId = "call-accept")
        val encoded = json.encodeToString(CallSignal.serializer(), signal)
        val decoded = json.decodeFromString(CallSignal.serializer(), encoded)
        assertEquals(CallSignalType.ACCEPT, decoded.type)
        assertEquals("call-accept", decoded.callId)
        assertNull(decoded.displayName)
    }

    @Test
    fun `decode signal with extra unknown fields`() {
        val payload = """{"type":"invite","callId":"abc","displayName":"Bob","extra":"ignored"}"""
        val decoded = json.decodeFromString(CallSignal.serializer(), payload)
        assertEquals(CallSignalType.INVITE, decoded.type)
        assertEquals("abc", decoded.callId)
        assertEquals("Bob", decoded.displayName)
    }
}
