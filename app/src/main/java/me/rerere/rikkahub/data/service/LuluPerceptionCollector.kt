package me.rerere.rikkahub.data.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.ai.tools.SystemTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.gadgetbridge.GadgetbridgeReader
import me.rerere.rikkahub.data.model.LuluAppUsageState
import me.rerere.rikkahub.data.model.LuluDeviceState
import me.rerere.rikkahub.data.model.LuluHealthState
import me.rerere.rikkahub.data.model.LuluPerceptionInput

class LuluPerceptionCollector(
    private val context: Context,
) {
    suspend fun collect(
        userText: String,
        settings: Settings,
        hourOfDay: Int = java.time.LocalDateTime.now().hour,
    ): LuluPerceptionInput = withContext(Dispatchers.IO) {
        val systemTools = settings.systemToolsSetting
        LuluPerceptionInput(
            userText = userText,
            hourOfDay = hourOfDay,
            deviceState = if (systemTools.batteryEnabled) collectDeviceState() else null,
            healthState = if (systemTools.gadgetbridgeEnabled) collectHealthState(settings) else null,
            appUsageState = if (
                systemTools.appUsageAccess || systemTools.appUsageEnabled
            ) {
                collectAppUsageState()
            } else {
                null
            },
        )
    }

    private fun collectDeviceState(): LuluDeviceState? = runCatching {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        LuluDeviceState(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            networkType = currentNetworkType(),
        )
    }.getOrNull()

    private fun currentNetworkType(): String? = runCatching {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return@runCatching null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@runCatching null
        when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }.getOrNull()

    private fun collectHealthState(settings: Settings): LuluHealthState? = runCatching {
        val path = settings.systemToolsSetting.gadgetbridgeDbPath
        if (!GadgetbridgeReader.dbFileExists(path)) return@runCatching null
        val latestActivity = GadgetbridgeReader.readLatestActivitySample(path)
        val latestDailySummary = GadgetbridgeReader.readDailySummaries(1, path).lastOrNull()
        val latestSleep = GadgetbridgeReader.readSleepSummaries(1, path).lastOrNull()

        LuluHealthState(
            sleepMinutes = latestSleep?.totalDuration,
            heartRate = latestActivity?.heartRate ?: latestDailySummary?.hrAvg,
            stepCount = latestDailySummary?.steps ?: latestActivity?.steps,
        )
    }.getOrNull()

    private suspend fun collectAppUsageState(): LuluAppUsageState? {
        if (!SystemTools.hasAppUsagePermission(context)) return null
        return runCatching {
            val usage = AppUsageService(context).getTodayUsageStats().getOrNull().orEmpty()
            LuluAppUsageState(
                topApps = usage.take(3).map { it.appName },
                screenMinutesToday = (usage.sumOf { it.totalTimeInForeground } / 60_000L).toInt(),
            )
        }.getOrNull()
    }
}
