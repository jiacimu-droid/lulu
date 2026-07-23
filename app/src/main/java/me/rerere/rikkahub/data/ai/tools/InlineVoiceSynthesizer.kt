package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.tts.controller.TtsChunk
import me.rerere.tts.controller.TtsSynthesizer
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.provider.TTSManager
import org.koin.core.context.GlobalContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

internal fun scheduleInlineVoiceMessage(
    context: Context,
    text: String,
): UIMessagePart.VoiceMessage {
    val cleanText = text.trim()
    require(cleanText.isNotBlank()) { "voice text is blank" }
    val directory = File(context.cacheDir, "inline-character-voice").apply { mkdirs() }
    val target = File(directory, "${UUID.randomUUID()}.voice")
    val estimatedDurationMs = estimateVoiceDurationMs(cleanText)

    GlobalContext.get().get<AppScope>().launch {
        synthesizeInlineVoiceToFile(
            context = context,
            text = cleanText,
            target = target,
        )
    }

    return UIMessagePart.VoiceMessage(
        url = target.absolutePath,
        duration = estimatedDurationMs,
        transcript = cleanText,
    )
}

internal suspend fun synthesizeInlineVoiceMessage(
    context: Context,
    text: String,
): Result<UIMessagePart.VoiceMessage> = runCatching {
    val cleanText = text.trim()
    require(cleanText.isNotBlank()) { "voice text is blank" }
    val directory = File(context.cacheDir, "inline-character-voice").apply { mkdirs() }
    val target = File(directory, "${UUID.randomUUID()}.voice")
    val durationMs = synthesizeInlineVoiceToFile(
        context = context,
        text = cleanText,
        target = target,
    )
    UIMessagePart.VoiceMessage(
        url = target.absolutePath,
        duration = durationMs,
        transcript = cleanText,
    )
}

private suspend fun synthesizeInlineVoiceToFile(
    context: Context,
    text: String,
    target: File,
): Long {
    val koin = GlobalContext.get()
    val settingsStore = koin.get<SettingsStore>()
    val provider = settingsStore.settingsFlow.value.getSelectedTTSProvider()
        ?: error("No TTS provider is selected")
    val response = TtsSynthesizer(koin.get<TTSManager>()).synthesize(
        setting = provider,
        chunk = TtsChunk(index = 0, text = text),
    )
    require(response.audioData.isNotEmpty()) { "TTS returned empty audio" }

    val playableBytes = if (response.format == AudioFormat.PCM) {
        pcm16MonoToWav(
            pcm = response.audioData,
            sampleRate = response.sampleRate ?: DEFAULT_PCM_SAMPLE_RATE,
        )
    } else {
        response.audioData
    }
    target.writeBytes(playableBytes)
    pruneInlineVoiceCache(File(context.cacheDir, "inline-character-voice"))

    return response.duration
        ?.takeIf { it > 0f }
        ?.let { (it * 1_000f).toLong() }
        ?: estimateVoiceDurationMs(text)
}

private fun estimateVoiceDurationMs(text: String): Long =
    ((text.length / 5.0).coerceAtLeast(1.0) * 1_000.0).toLong()

private fun pcm16MonoToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
    val output = ByteArrayOutputStream(WAV_HEADER_SIZE + pcm.size)
    val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    val byteRate = sampleRate * PCM_CHANNELS * PCM_BITS_PER_SAMPLE / 8
    val blockAlign = (PCM_CHANNELS * PCM_BITS_PER_SAMPLE / 8).toShort()

    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + pcm.size)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1.toShort())
    header.putShort(PCM_CHANNELS.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort(blockAlign)
    header.putShort(PCM_BITS_PER_SAMPLE.toShort())
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(pcm.size)

    output.write(header.array())
    output.write(pcm)
    return output.toByteArray()
}

private fun pruneInlineVoiceCache(directory: File) {
    directory.listFiles()
        ?.filter(File::isFile)
        ?.sortedByDescending(File::lastModified)
        ?.drop(MAX_INLINE_VOICE_FILES)
        ?.forEach { file -> file.delete() }
}

private const val DEFAULT_PCM_SAMPLE_RATE = 24_000
private const val PCM_CHANNELS = 1
private const val PCM_BITS_PER_SAMPLE = 16
private const val WAV_HEADER_SIZE = 44
private const val MAX_INLINE_VOICE_FILES = 80
