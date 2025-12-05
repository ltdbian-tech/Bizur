package com.bizur.android.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bizur.android.model.Message
import com.bizur.android.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ChatsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun messageBubble_setsContentDescription_withStatus() {
        val message = Message(
            id = "msg-1",
            conversationId = "conv",
            senderId = "self",
            body = "Hello",
            sentAtEpochMillis = 0L,
            status = MessageStatus.Delivered,
            attachmentPath = null,
            attachmentMimeType = null,
            attachmentDisplayName = null
        )

        composeTestRule.setContent {
            MessageBubble(
                message = message,
                isOwnMessage = true,
                onOpenAttachment = {},
                onShareAttachment = {},
                onResolveAttachment = { null },
                onReply = {},
                onLongPress = {},
                onRetry = {},
                reaction = null,
                onReact = {}
            )
        }

        composeTestRule
            .onNodeWithTag("message-bubble-${message.id}")
            .assertExists()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("Sent message, Delivered")
                )
            )
    }

    @Test
    fun messageInputBar_enablesSendWhenDraftPresent() {
        val sendCount = AtomicInteger(0)
        composeTestRule.setContent {
            var draft by remember { mutableStateOf("") }
            MessageInputBar(
                draft = draft,
                onDraftChanged = { draft = it },
                canAttach = true,
                canSend = draft.isNotBlank(),
                onAttach = {},
                onSend = { sendCount.incrementAndGet() }
            )
        }

        val sendButton = composeTestRule.onNodeWithContentDescription("Send message")
        sendButton.assertIsNotEnabled()

        composeTestRule
            .onNodeWithText("Send an encrypted whisper")
            .performTextInput("Hi")

        sendButton.assertIsEnabled()
        sendButton.performClick()

        assertEquals(1, sendCount.get())
    }
}
