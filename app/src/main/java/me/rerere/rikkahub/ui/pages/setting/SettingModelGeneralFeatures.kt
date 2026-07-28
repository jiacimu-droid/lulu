package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Chatting01
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.FileZip
import me.rerere.hugeicons.stroke.Image01
import me.rerere.hugeicons.stroke.MessageMultiple01
import me.rerere.hugeicons.stroke.Notebook01
import me.rerere.hugeicons.stroke.Video01
import me.rerere.hugeicons.stroke.View
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.datastore.Settings

@Composable
internal fun DefaultChatModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_chat_model),
        description = stringResource(R.string.setting_model_page_chat_model_desc),
        icon = HugeIcons.Chatting01,
        modelId = settings.chatModelId,
        type = ModelType.CHAT,
        settings = settings,
        onSelect = { model -> vm.updateSettings(settings.copy(chatModelId = model.id)) },
    )
}

@Composable
internal fun DefaultLuluModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_lulu_intent_model),
        description = stringResource(R.string.setting_model_page_lulu_intent_model_desc),
        icon = HugeIcons.MessageMultiple01,
        modelId = settings.luluIntentModelId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = true,
        onSelect = { model ->
            vm.updateSettings(
                settings.copy(luluIntentModelId = model.id.takeUnless { model.modelId.isBlank() }),
            )
        },
    )
}

@Composable
internal fun DefaultTheaterModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_theater_model),
        description = stringResource(R.string.setting_model_page_theater_model_desc),
        icon = HugeIcons.BookOpen02,
        modelId = settings.theaterModelId,
        type = ModelType.CHAT,
        settings = settings,
        onSelect = { model ->
            vm.updateSettings(
                settings.copy(theaterModelId = model.id.takeUnless { model.modelId.isBlank() }),
            )
        },
    )
}

@Composable
internal fun DefaultImageGenerationModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_image_generation_model),
        description = stringResource(R.string.setting_model_page_image_generation_model_desc),
        icon = HugeIcons.Image01,
        modelId = settings.imageGenerationModelId,
        type = ModelType.IMAGE,
        settings = settings,
        onSelect = { model -> vm.updateSettings(settings.copy(imageGenerationModelId = model.id)) },
    )
}

@Composable
internal fun DefaultVideoGenerationModelSetting(settings: Settings, vm: SettingVM) {
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_video_generation_model),
        description = stringResource(R.string.setting_model_page_video_generation_model_desc),
        icon = HugeIcons.Video01,
        modelId = settings.videoGenerationModelId,
        type = ModelType.VIDEO,
        settings = settings,
        onSelect = { model -> vm.updateSettings(settings.copy(videoGenerationModelId = model.id)) },
    )
}

@Composable
internal fun DefaultTranslationModelSetting(settings: Settings, vm: SettingVM) {
    PromptModelFeature(
        title = stringResource(R.string.setting_model_page_translate_model),
        description = stringResource(R.string.setting_model_page_translate_model_desc),
        icon = HugeIcons.Earth,
        modelId = settings.translateModeId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = false,
        prompt = settings.translatePrompt,
        promptDescription = stringResource(R.string.setting_model_page_translate_prompt_vars),
        defaultPrompt = DEFAULT_TRANSLATION_PROMPT,
        onSelect = { model -> vm.updateSettings(settings.copy(translateModeId = model.id)) },
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
        onSelect = { model -> vm.updateSettings(settings.copy(titleModelId = model.id)) },
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
        onSelect = { model -> vm.updateSettings(settings.copy(ocrModelId = model.id)) },
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
        onSelect = { model -> vm.updateSettings(settings.copy(compressModelId = model.id)) },
        onPromptChange = { vm.updateSettings(settings.copy(compressPrompt = it)) },
    )
}
