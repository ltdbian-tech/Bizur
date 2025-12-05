package com.bizur.android.data

object SeedData {
    fun initialState(): BizurDataState {
        return BizurDataState(
            identityCode = "self",
            contacts = emptyList(),
            conversations = emptyMap(),
            messages = emptyMap(),
            callLogs = emptyList(),
            draft = ""
        )
    }
}
