package me.rerere.rikkahub.ui.pages.imggen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.service.ImageGenerationWorker
import java.io.File
import java.util.UUID

@Serializable
data class GeneratedImage(
    val id: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String,
)

private fun GenMediaEntity.toGeneratedImage(filesManager: FilesManager): GeneratedImage {
    val imagesDir = filesManager.getImagesDir()
    val fullPath = File(imagesDir, path.removePrefix("images/")).absolutePath
    return GeneratedImage(
        id = id,
        prompt = prompt,
        filePath = fullPath,
        timestamp = createAt,
        model = modelId,
    )
}

internal fun mergeEffectiveReferenceImages(
    assistantFaceReference: String?,
    manualReferences: List<String>,
    maxImages: Int = 16,
): List<String> = buildList {
    assistantFaceReference?.takeIf { it.isNotBlank() }?.let(::add)
    manualReferences.filter { it.isNotBlank() }.forEach(::add)
}.distinct().take(maxImages)

class ImgGenVM(
    context: Application,
    val settingsStore: SettingsStore,
    @Suppress("unused") val providerManager: ProviderManager,
    val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
) : AndroidViewModel(context) {
    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _numberOfImages = MutableStateFlow(1)
    val numberOfImages: StateFlow<Int> = _numberOfImages

    private val _aspectRatio = MutableStateFlow(ImageAspectRatio.PORTRAIT)
    val aspectRatio: StateFlow<ImageAspectRatio> = _aspectRatio

    // Generation now belongs to WorkManager rather than this page. Keeping this flow preserves the
    // existing UI contract without trapping the user behind a loading screen.
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentGeneratedImages = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val currentGeneratedImages: StateFlow<List<GeneratedImage>> = _currentGeneratedImages

    private val _referenceImages = MutableStateFlow<List<String>>(emptyList())
    val referenceImages: StateFlow<List<String>> = _referenceImages

    private val _lastQueuedTask = MutableStateFlow<UUID?>(null)
    val lastQueuedTask: StateFlow<UUID?> = _lastQueuedTask

    private var appliedInitialRequestKey: String? = null

    val pager = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { genMediaRepository.getAllMedia() },
    )
    val generatedImages: Flow<PagingData<GeneratedImage>> = pager.flow
        .map { pagingData -> pagingData.map { it.toGeneratedImage(filesManager) } }
        .cachedIn(viewModelScope)

    fun updatePrompt(prompt: String) {
        _prompt.value = prompt
    }

    fun updateNumberOfImages(count: Int) {
        _numberOfImages.value = count.coerceIn(1, 4)
    }

    fun updateAspectRatio(aspectRatio: ImageAspectRatio) {
        _aspectRatio.value = aspectRatio
    }

    fun applyInitialRequest(prompt: String, count: Int, autoGenerate: Boolean) {
        if (prompt.isBlank()) return
        val key = "$prompt|$count|$autoGenerate"
        if (appliedInitialRequestKey == key) return
        appliedInitialRequestKey = key
        _prompt.value = prompt
        _numberOfImages.value = count.coerceIn(1, 4)
        if (autoGenerate) generateImage()
    }

    fun addReferenceImages(paths: List<String>) {
        _referenceImages.value = (_referenceImages.value + paths).distinct().take(MAX_REFERENCE_IMAGES)
    }

    fun removeReferenceImage(path: String) {
        _referenceImages.value = _referenceImages.value.filterNot { it == path }
        deleteReferenceFiles(listOf(path))
    }

    fun clearReferenceImages() {
        deleteReferenceFiles(_referenceImages.value)
        _referenceImages.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }

    fun startNewSession() {
        clearReferenceImages()
        _prompt.value = ""
        _currentGeneratedImages.value = emptyList()
        _error.value = null
        _isGenerating.value = false
    }

    /** Queue a normal text-to-image request and return immediately. */
    fun generateImage() {
        enqueueBackgroundGeneration(emptyList())
    }

    /**
     * Queue either image editing or text-to-image generation. Reference files are copied into a
     * durable private task directory before this method returns, so leaving the page cannot remove
     * data that the worker still needs.
     */
    fun editImage() {
        enqueueBackgroundGeneration(_referenceImages.value)
    }

    private fun enqueueBackgroundGeneration(manualReferences: List<String>) {
        val cleanPrompt = _prompt.value.trim()
        if (cleanPrompt.isBlank()) return
        try {
            val taskId = ImageGenerationWorker.enqueue(
                context = getApplication(),
                prompt = cleanPrompt,
                count = _numberOfImages.value,
                aspectRatio = _aspectRatio.value,
                referenceImages = manualReferences,
            )
            _lastQueuedTask.value = taskId
            _currentGeneratedImages.value = emptyList()
            _error.value = null
            _isGenerating.value = false
            // Temporary reference files are safe to remove because enqueue() copied them first.
            if (manualReferences.isNotEmpty()) clearReferenceImages()
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to enqueue background image generation", error)
            _error.value = error.toFriendlyImageError()
        }
    }

    fun cancelGeneration() {
        _lastQueuedTask.value?.let { WorkManager.getInstance(getApplication()).cancelWorkById(it) }
        _lastQueuedTask.value = null
        _isGenerating.value = false
    }

    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            try {
                genMediaRepository.deleteMedia(image.id)
                File(image.filePath).takeIf(File::exists)?.delete()
            } catch (error: Exception) {
                Log.e(TAG, "Failed to delete image", error)
                _error.value = "删除图片失败"
            }
        }
    }

    private fun deleteReferenceFiles(paths: List<String>) {
        viewModelScope.launch {
            paths.forEach { path -> File(path).takeIf(File::exists)?.delete() }
        }
    }

    companion object {
        private const val TAG = "ImgGenVM"
        private const val MAX_REFERENCE_IMAGES = 16
    }
}

internal fun String.toSafeFileNamePart(): String =
    replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), "_")
        .trim('_')
        .ifBlank { "image_model" }
        .take(80)

private fun Throwable.toFriendlyImageError(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("not supported model for image generation", ignoreCase = true) ||
            raw.contains("only imagen models are supported", ignoreCase = true) ||
            raw.contains("bad_response_status_code", ignoreCase = true) ||
            raw.contains("404", ignoreCase = true) ->
            "生图模型不匹配或接口不支持当前模型。请重新选择 IMAGE 类型模型。原始错误：$raw"
        raw.isBlank() -> "生图任务没有成功加入后台队列，请检查模型和网络设置。"
        else -> raw
    }
}
