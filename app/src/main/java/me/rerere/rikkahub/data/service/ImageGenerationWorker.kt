package me.rerere.rikkahub.data.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.ui.pages.imggen.toSafeFileNamePart
import me.rerere.rikkahub.utils.NotificationUtil
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Long-running, restartable image generation.
 *
 * The provider request is no longer owned by an ImageGen ViewModel, so leaving the page does not
 * cancel it. WorkManager keeps an ongoing notification while the request runs and retries after
 * transient process/network failures. Completed files are inserted into GenMediaRepository, which
 * is also the data source used by the Star Wish gallery.
 */
class ImageGenerationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID).orEmpty()
        val prompt = inputData.getString(KEY_PROMPT).orEmpty().trim()
        val count = inputData.getInt(KEY_COUNT, 1).coerceIn(1, 4)
        val aspectRatio = runCatching {
            ImageAspectRatio.valueOf(inputData.getString(KEY_ASPECT_RATIO).orEmpty())
        }.getOrDefault(ImageAspectRatio.PORTRAIT)
        val referenceImages = inputData.getStringArray(KEY_REFERENCE_IMAGES)
            .orEmpty()
            .map(::File)
            .filter(File::exists)
            .map(File::getAbsolutePath)

        if (taskId.isBlank() || prompt.isBlank()) return Result.failure()
        setForeground(createForegroundInfo(taskId, "正在准备画布…"))

        val koin = org.koin.core.context.GlobalContext.get()
        val settingsStore = koin.get<SettingsStore>()
        val providerManager = koin.get<ProviderManager>()
        val mediaRepository = koin.get<GenMediaRepository>()
        val filesManager = koin.get<FilesManager>()

        return try {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.imageGenerationModelId)
                ?: error("当前没有选择图片生成模型")
            require(model.type == ModelType.IMAGE) { "当前默认模型不是 IMAGE 类型" }
            val provider = model.findProvider(settings.providers)
                ?: error("找不到生图模型对应的提供商")
            val providerSetting = settings.providers.firstOrNull { it.id == provider.id }
                ?: error("找不到生图提供商配置")

            setForeground(createForegroundInfo(taskId, "露露正在慢慢画，可以退出页面"))
            var resultType = GenMediaEntity.TYPE_IMAGE_GENERATION
            val result = if (referenceImages.isEmpty()) {
                providerManager.getProviderByType(provider).generateImage(
                    providerSetting,
                    ImageGenerationParams(
                        model = model,
                        prompt = prompt,
                        numOfImages = count,
                        aspectRatio = aspectRatio,
                        customHeaders = model.customHeaders,
                        customBody = model.customBodies,
                    ),
                )
            } else {
                runCatching {
                    resultType = GenMediaEntity.TYPE_IMAGE_EDIT
                    providerManager.getProviderByType(provider).editImage(
                        providerSetting,
                        ImageEditParams(
                            model = model,
                            prompt = prompt,
                            images = referenceImages,
                            numOfImages = count,
                            aspectRatio = aspectRatio,
                            customHeaders = model.customHeaders,
                            customBody = model.customBodies,
                        ),
                    )
                }.getOrElse {
                    resultType = GenMediaEntity.TYPE_IMAGE_GENERATION
                    providerManager.getProviderByType(provider).generateImage(
                        providerSetting,
                        ImageGenerationParams(
                            model = model,
                            prompt = "请保持参考角色的脸型、眼睛、发型和整体气质一致。\n$prompt",
                            numOfImages = count,
                            aspectRatio = aspectRatio,
                            customHeaders = model.customHeaders,
                            customBody = model.customBodies,
                        ),
                    )
                }
            }

            result.items.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                saveImage(
                    item = item,
                    prompt = prompt,
                    modelName = model.displayName,
                    index = index,
                    type = resultType,
                    sourcePaths = referenceImages.takeIf(List<String>::isNotEmpty)?.joinToString("|"),
                    filesManager = filesManager,
                    mediaRepository = mediaRepository,
                )
            }
            cleanupTaskFiles(taskId)
            notifyFinished(taskId, result.items.size)
            Result.success(Data.Builder().putInt(KEY_RESULT_COUNT, result.items.size).build())
        } catch (error: Throwable) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                cleanupTaskFiles(taskId)
                notifyFailed(taskId, error.message ?: "图片生成失败")
                Result.failure(Data.Builder().putString(KEY_ERROR, error.message).build())
            }
        }
    }

    private suspend fun saveImage(
        item: ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
        type: String,
        sourcePaths: String?,
        filesManager: FilesManager,
        mediaRepository: GenMediaRepository,
    ) {
        val timestamp = System.currentTimeMillis()
        val imageFile = File(
            filesManager.getImagesDir(),
            "${timestamp}_${modelName.toSafeFileNamePart()}_$index.png",
        )
        when {
            item.sourceUrl != null -> download(item.sourceUrl!!, imageFile)
            item.data != null -> filesManager.createImageFileFromBase64(item.data!!, imageFile.absolutePath)
            else -> error("生图接口没有返回图片地址或图片数据")
        }
        mediaRepository.insertMedia(
            GenMediaEntity(
                path = "images/${imageFile.name}",
                modelId = modelName,
                prompt = prompt,
                createAt = timestamp,
                type = type,
                sourcePaths = sourcePaths,
            ),
        )
    }

    private suspend fun download(url: String, target: File): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 45_000
            readTimeout = 180_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("下载生成图片失败：HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        } finally {
            connection.disconnect()
        }
    }

    private fun createForegroundInfo(taskId: String, content: String): ForegroundInfo {
        val notification = NotificationUtil.buildNotification(
            applicationContext,
            CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            me.rerere.rikkahub.utils.NotificationConfig().apply {
                title = "露露正在画画"
                this.content = content
                ongoing = true
                onlyAlertOnce = true
                category = NotificationCompat.CATEGORY_PROGRESS
            },
        ).setProgress(0, 0, true).build()
        return ForegroundInfo(notificationId(taskId), notification)
    }

    private fun notifyFinished(taskId: String, count: Int) {
        NotificationUtil.notify(
            applicationContext,
            CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId(taskId),
        ) {
            title = "露露画好啦"
            content = "$count 张图片已经放进心愿馆和生图图库"
            autoCancel = true
            useDefaults = true
            contentIntent = openAppIntent(taskId)
        }
    }

    private fun notifyFailed(taskId: String, reason: String) {
        NotificationUtil.notify(
            applicationContext,
            CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId(taskId),
        ) {
            title = "这次没有画成功"
            content = reason.take(180)
            autoCancel = true
            contentIntent = openAppIntent(taskId)
        }
    }

    private fun openAppIntent(taskId: String): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        notificationId(taskId),
        Intent(applicationContext, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cleanupTaskFiles(taskId: String) {
        File(applicationContext.filesDir, "$QUEUE_DIR/$taskId").deleteRecursively()
    }

    private fun notificationId(taskId: String): Int = taskId.hashCode().let {
        if (it == Int.MIN_VALUE) 17001 else kotlin.math.abs(it)
    }

    companion object {
        private const val KEY_TASK_ID = "image_task_id"
        private const val KEY_PROMPT = "image_prompt"
        private const val KEY_COUNT = "image_count"
        private const val KEY_ASPECT_RATIO = "image_aspect_ratio"
        private const val KEY_REFERENCE_IMAGES = "image_reference_paths"
        private const val KEY_RESULT_COUNT = "image_result_count"
        private const val KEY_ERROR = "image_error"
        private const val QUEUE_DIR = "image_generation_queue"
        private const val MAX_RETRIES = 2

        fun enqueue(
            context: Context,
            prompt: String,
            count: Int,
            aspectRatio: ImageAspectRatio,
            referenceImages: List<String>,
        ): UUID {
            val taskId = UUID.randomUUID().toString()
            val taskDir = File(context.filesDir, "$QUEUE_DIR/$taskId").apply { mkdirs() }
            val durableReferences = referenceImages.mapIndexedNotNull { index, path ->
                runCatching {
                    val source = File(path)
                    if (!source.exists()) return@runCatching null
                    val target = File(taskDir, "reference_$index.${source.extension.ifBlank { "png" }}")
                    source.copyTo(target, overwrite = true)
                    target.absolutePath
                }.getOrNull()
            }
            val request = OneTimeWorkRequestBuilder<ImageGenerationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(
                    Data.Builder()
                        .putString(KEY_TASK_ID, taskId)
                        .putString(KEY_PROMPT, prompt.trim())
                        .putInt(KEY_COUNT, count.coerceIn(1, 4))
                        .putString(KEY_ASPECT_RATIO, aspectRatio.name)
                        .putStringArray(KEY_REFERENCE_IMAGES, durableReferences.toTypedArray())
                        .build(),
                )
                .addTag("image_generation")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "image_generation:$taskId",
                ExistingWorkPolicy.KEEP,
                request,
            )
            return request.id
        }
    }
}
