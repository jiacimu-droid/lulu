package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri

fun resolveAppVideoUri(context: Context, uri: String): Uri {
    if (!uri.startsWith(RAW_VIDEO_PREFIX)) return uri.toUri()
    val rawName = uri.removePrefix(RAW_VIDEO_PREFIX).substringBeforeLast('.')
    val rawId = context.resources.getIdentifier(rawName, "raw", context.packageName)
    return "android.resource://${context.packageName}/$rawId".toUri()
}

private const val RAW_VIDEO_PREFIX = "raw:"
