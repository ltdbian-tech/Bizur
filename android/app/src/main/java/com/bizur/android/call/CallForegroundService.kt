package com.bizur.android.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bizur.android.BizurApplication
import com.bizur.android.MainActivity
import com.bizur.android.R
import com.bizur.android.model.CallDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CallForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { (application as BizurApplication).container.repository }
    private var ringtone: Ringtone? = null
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CALL_CHANNEL_ID,
                getString(R.string.notification_channel_calls),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_calls_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANG_UP -> {
                serviceScope.launch { repository.endCall() }
                stopCallAlerts()
                return START_NOT_STICKY
            }
            ACTION_ACCEPT -> {
                serviceScope.launch { repository.acceptIncomingCall() }
                stopCallAlerts()
                return START_NOT_STICKY
            }
            ACTION_DECLINE -> {
                serviceScope.launch { repository.declineIncomingCall() }
                stopCallAlerts()
                return START_NOT_STICKY
            }
        }
        val displayName = intent?.getStringExtra(EXTRA_DISPLAY_NAME) ?: getString(R.string.app_name)
        val statusName = intent?.getStringExtra(EXTRA_STATUS)
        val status = statusName?.let { runCatching { CallStatus.valueOf(it) }.getOrNull() } ?: CallStatus.Connected
        val directionName = intent?.getStringExtra(EXTRA_DIRECTION)
        val direction = directionName?.let { runCatching { CallDirection.valueOf(it) }.getOrNull() }
        val notification = buildNotification(displayName, status, direction)
        updateCallAlerts(status, direction)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopCallAlerts()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(displayName: String, status: CallStatus, direction: CallDirection?): Notification {
        val contentText = when (status) {
            CallStatus.Calling -> getString(R.string.notification_call_outgoing, displayName)
            CallStatus.Ringing -> getString(R.string.notification_call_incoming, displayName)
            CallStatus.Connected -> getString(R.string.notification_call_connected, displayName)
            CallStatus.Idle -> getString(R.string.app_name)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        when {
            status == CallStatus.Ringing && direction == CallDirection.Incoming -> {
                val acceptAction = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_call,
                    getString(R.string.notification_action_answer),
                    actionPendingIntent(ACTION_ACCEPT, REQUEST_ACCEPT)
                ).build()
                val declineAction = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notification_action_decline),
                    actionPendingIntent(ACTION_DECLINE, REQUEST_DECLINE)
                ).build()
                builder.addAction(acceptAction)
                builder.addAction(declineAction)
            }
            status == CallStatus.Calling || status == CallStatus.Connected -> {
                val hangUpAction = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notification_action_hang_up),
                    actionPendingIntent(ACTION_HANG_UP, REQUEST_HANG_UP)
                ).build()
                builder.addAction(hangUpAction)
            }
        }
        return builder.build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, CallForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateCallAlerts(status: CallStatus, direction: CallDirection?) {
        if (status == CallStatus.Ringing && direction == CallDirection.Incoming) {
            startCallAlerts()
        } else {
            stopCallAlerts()
        }
    }

    private fun startCallAlerts() {
        if (ringtone?.isPlaying != true) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                }
                play()
            }
        }
        val vibrationPattern = longArrayOf(0, 600, 400)
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(vibrationPattern, 0)
            }
        }
    }

    private fun stopCallAlerts() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    companion object {
        private const val CALL_CHANNEL_ID = "bizur_calls"
        private const val NOTIFICATION_ID = 0xB1A0
        private const val EXTRA_DISPLAY_NAME = "extra_display_name"
        private const val EXTRA_STATUS = "extra_status"
        private const val EXTRA_DIRECTION = "extra_direction"
        private const val ACTION_HANG_UP = "com.bizur.android.call.action.HANG_UP"
        private const val ACTION_ACCEPT = "com.bizur.android.call.action.ACCEPT"
        private const val ACTION_DECLINE = "com.bizur.android.call.action.DECLINE"
        private const val REQUEST_ACCEPT = 0xC1
        private const val REQUEST_DECLINE = 0xC2
        private const val REQUEST_HANG_UP = 0xC3

        fun update(context: Context, state: CallSessionState) {
            val appContext = context.applicationContext
            if (state.status == CallStatus.Idle) {
                stop(appContext)
            } else {
                start(appContext, state)
            }
        }

        private fun start(context: Context, state: CallSessionState) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_DISPLAY_NAME, state.displayName)
                putExtra(EXTRA_STATUS, state.status.name)
                putExtra(EXTRA_DIRECTION, state.direction?.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        private fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
