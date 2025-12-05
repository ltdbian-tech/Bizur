package com.bizur.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.bizur.android.ui.BizurApp
import com.bizur.android.ui.theme.BizurTheme
import com.bizur.android.viewmodel.BizurViewModelFactory
import com.bizur.android.notifications.MessageNotifier

val LocalBizurViewModelFactory = staticCompositionLocalOf<BizurViewModelFactory> {
    error("BizurViewModelFactory not provided")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val application = (applicationContext as BizurApplication)
        val factory = BizurViewModelFactory(application.container.repository)
        val startConversationId = intent?.getStringExtra(MessageNotifier.EXTRA_CONVERSATION_ID)

        setContent {
            BizurTheme {
                CompositionLocalProvider(LocalBizurViewModelFactory provides factory) {
                    BizurApp(startConversationId = startConversationId)
                }
            }
        }
    }
}
