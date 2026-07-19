package me.rerere.rikkahub.data.voicecall

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import me.rerere.rikkahub.INCOMING_VOICE_CALL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.ProactiveCallSetting
import me.rerere.rikkahub.data.datastore.shouldUseProactiveCallChannel
import java.time.LocalDateTime
import kotlin.math.absoluteValue

object ProactiveCallManager {
    const val ACTION_INCOMING_CALL = "me.rerere.rikkahub.action.PROACTIVE_INCOMING_CALL"
    const val ACTION_DECLINE_CALL = "me.rerere.rikkahub.action.PROACTIVE_DECLINE_CALL"
    const val EXTRA_ASSISTANT_ID = "proactive_call_assistant_id"
    const val EXTRA_ASSISTANT_NAME = "proactive_call_assistant_name"
    const val EXTRA_CONVERSATION_ID = "proactive_call_conversation_id"
    const val EXTRA_REASON = "proactive_call_reason"
    const val EXTRA_AUTO_START = "proactive_call_auto_start"

    private const val PREFS_NAME = "proactive_call_state"
    private const val KEY_LAST_CALL_PREFIX = "last_call:"
    private const val NOTIFICATION_ID = 2411
    private const val RING_TIMEOUT_MILLIS = 60_000L

    fun shouldOffer(
        context: Context,
        assistantId: String,
        setting: ProactiveCallSetting,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!setting.allowMobileData && !isOnWifi(context)) return false
        val lastCall = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CALL_PREFIX + assistantId, 0L)
        val selector = ((nowMillis / 60_000L).hashCode() xor assistantId.hashCode()).absoluteValue % 100
        return shouldUseProactiveCallChannel(
            setting = setting,
            localHour = LocalDateTime.now().hour,
            millisSinceLastCall = nowMillis - lastCall,
            selector = selector,
        )
    }

    fun offerIncomingCall(
        context: Context,
        assistantId: String,
        assistantName: String,
        conversationId: String,
        reason: String,
        setting: ProactiveCallSetting,
        force: Boolean = false,
    ): Boolean {
        if (!force && !shouldOffer(context, assistantId, setting)) return false
        val appContext = context.applicationContext
        val ringingIntent = routeIntent(
            context = appContext,
            assistantId = assistantId,
            assistantName = assistantName,
            conversationId = conversationId,
            reason = reason,
            autoStart = false,
        )
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            markCallOffered(appContext, assistantId)
            appContext.startActivity(ringingIntent)
            return true
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val person = Person.Builder()
            .setName(assistantName)
            .setImportant(true)
            .build()
        val declineIntent = Intent(appContext, ProactiveCallActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            appContext,
            2412,
            declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val answerPendingIntent = PendingIntent.getActivity(
            appContext,
            2413,
            routeIntent(
                context = appContext,
                assistantId = assistantId,
                assistantName = assistantName,
                conversationId = conversationId,
                reason = reason,
                autoStart = true,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ringingPendingIntent = PendingIntent.getActivity(
            appContext,
            2414,
            ringingIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(appContext, INCOMING_VOICE_CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(assistantName)
            .setContentText("语音来电")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_TIMEOUT_MILLIS)
            .setContentIntent(ringingPendingIntent)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declinePendingIntent, answerPendingIntent))
            .addPerson(person)
        if (setting.fullScreenWhenAllowed) {
            builder.setFullScreenIntent(ringingPendingIntent, true)
        }
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        markCallOffered(appContext, assistantId)
        return true
    }

    fun dismissIncomingCall(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun routeIntent(
        context: Context,
        assistantId: String,
        assistantName: String,
        conversationId: String,
        reason: String,
        autoStart: Boolean,
    ) = Intent(context, RouteActivity::class.java).apply {
        action = ACTION_INCOMING_CALL
        putExtra(EXTRA_ASSISTANT_ID, assistantId)
        putExtra(EXTRA_ASSISTANT_NAME, assistantName)
        putExtra(EXTRA_CONVERSATION_ID, conversationId)
        putExtra(EXTRA_REASON, reason)
        putExtra(EXTRA_AUTO_START, autoStart)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private fun markCallOffered(context: Context, assistantId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CALL_PREFIX + assistantId, System.currentTimeMillis())
            .apply()
    }

    private fun isOnWifi(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

class ProactiveCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ProactiveCallManager.ACTION_DECLINE_CALL) {
            ProactiveCallManager.dismissIncomingCall(context)
        }
    }
}
