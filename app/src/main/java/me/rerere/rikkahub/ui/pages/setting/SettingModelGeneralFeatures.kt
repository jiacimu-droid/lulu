package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiImage
import me.rerere.hugeicons.stroke.AiVideo
import me.rerere.hugeicons.stroke.Compress
import me.rerere.hugeicons.stroke.FileZip
import me.rerere.hugeicons.stroke.LanguageSkill
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Notebook01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.View
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.datastore.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.datastore.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.datastore.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.datastore.Settings

@Composable
internal fun DefaultChatModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_chat_model),
        description = stringResource(R.string.setting_model_page_chat_model_desc),
        icon = HugeIcons.Message01,
        modelId = settings.chatModelId,
        type = ModelType.CHAT,
        settings = settings,
        onSelect = { vm.updateSettings(settings.copy(chatModelId = it)) },
    )
}

@Composable
internal fun DefaultLuluModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_lulu_model),
        description = stringResource(R.string.setting_model_page_lulu_model_desc),
        icon = HugeIcons.Sparkles,
        modelId = settings.luluChatModelId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = true,
        onSelect = { vm.updateSettings(settings.copy(luluChatModelId = it)) },
    )
}

@Composable
internal fun DefaultTheaterModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_theater_model),
        description = stringResource(R.string.setting_model_page_theater_model_desc),
        icon = HugeIcons.Notebook01,
        modelId = settings.theaterChatModelId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = true,
        onSelect = { vm.updateSettings(settings.copy(theaterChatModelId = it)) },
    )
}

@Composable
internal fun DefaultImageModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_image_model),
        description = stringResource(R.string.setting_model_page_image_model_desc),
        icon = HugeIcons.AiImage,
        modelId = settings.imageGenerationModelId,
        type = ModelType.IMAGE,
        settings = settings,
        onSelect = { vm.updateSettings(settings.copy(imageGenerationModelId = it)) },
    )
}

@Composable
internal fun DefaultVideoModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_video_model),
        description = stringResource(R.string.setting_model_page_video_model_desc),
        icon = HugeIcons.AiVideo,
        modelId = settings.videoGenerationModelId,
        type = ModelType.VIDEO,
        settings = settings,
        onSelect = { vm.updateSettings(settings.copy(videoGenerationModelId = it)) },
    )
}

@Composable
internal fun DefaultTranslationModelSetting(settings: Settings, vm: SettingVM) {
    PromptModelFeature(
        title = stringResource(R.string.setting_model_page_translate_model),
        description = stringResource(R.string.setting_model_page_translate_model_desc),
        icon = HugeIcons.LanguageSkill,
        modelId = settings.translateModelId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = false,
        prompt = settings.translatePrompt,
        promptDescription = stringResource(R.string.setting_model_page_translate_prompt_vars),
        defaultPrompt = DEFAULT_TRANSLATION_PROMPT,
        onSelect = { vm.updateSettings(settings.copy(translateModelId = it)) },
        onPromptChange = { vm.updateSettings(settings.copy(translatePrompt = it)) },
        thinkingBudget = settings.translateThinkingBudget,
        onThinkingBudgetChange = { vm.updateSettings(settings.copy(translateThinkingBudget = it)) },
    )
}

@Composable
internal fun DefaultTitleModelSetting(settings: Settings, vm: SettingVM) {
    PromptModelFeature(
        title = stringResource(R.string.setting_model_page_title_model),
        description = stringResource(R.string.setting_model_page_title_model_desc),
        icon = HugeIcons.Notebook01,
        modelId = settings.titleModelId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = true,
        prompt = settings.titlePrompt,
        promptDescription = stringResource(R.string.setting_model_page_suggestion_prompt_vars),
        defaultPrompt = DEFAULT_TITLE_PROMPT,
        onSelect = { vm.updateSettings(settings.copy(titleModelId = it)) },
        onPromptChange = { vm.updateSettings(settings.copy(titlePrompt = it)) },
    )
}

@Composable
internal fun DefaultOcrModelSetting(settings: Settings, vm: SettingVM) {
    PromptModelFeature(
        title = stringResource(R.string.setting_model_page_ocr_model),
        description = stringResource(R.string.setting_model_page_ocr_model_desc),
        icon = HugeIcons.View,
        modelId = settings.ocrModelId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = false,
        prompt = settings.ocrPrompt,
        promptDescription = stringResource(R.string.setting_model_page_ocr_prompt_vars),
        defaultPrompt = DEFAULT_OCR_PROMPT,
        onSelect = { vm.updateSettings(settings.copy(ocrModelId = it)) },
        onPromptChange = { vm.updateSettings(settings.copy(ocrPrompt = it)) },
    )
}

@Composable
internal fun DefaultCompressModelSetting(settings: Settings, vm: SettingVM) {
    PromptModelFeature(
        title = stringResource(R.string.setting_model_page_compress_model),
        description = stringResource(R.string.setting_model_page_compress_model_desc),
        icon = HugeIcons.FileZip,
        modelId = settings.compressModelId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = false,
        prompt = settings.compressPrompt,
        promptDescription = stringResource(R.string.setting_model_page_compress_prompt_vars),
        defaultPrompt = DEFAULT_COMPRESS_PROMPT,
        onSelect = { vm.updateSettings(settings.copy(compressModelId = it)) },
        onPromptChange = { vm.updateSettings(settings.copy(compressPrompt = it)) },
    )
}
