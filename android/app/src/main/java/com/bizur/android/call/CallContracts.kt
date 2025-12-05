package com.bizur.android.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CALL_MIME_TYPE = "application/bizur-call+json"

@Serializable
enum class CallSignalType {
    @SerialName("invite") INVITE,
    @SerialName("accept") ACCEPT,
    @SerialName("end") END
}

@Serializable
data class CallSignal(
    val type: CallSignalType,
    val callId: String,
    val displayName: String? = null
)

enum class CallStatus { Idle, Calling, Ringing, Connected }

data class CallSessionState(
    val status: CallStatus = CallStatus.Idle,
    val callId: String? = null,
    val peerId: String? = null,
    val displayName: String = "",
    val direction: com.bizur.android.model.CallDirection? = null,
    val startedAtMillis: Long? = null
)
