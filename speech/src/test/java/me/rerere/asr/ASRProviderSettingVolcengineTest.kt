package me.rerere.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class ASRProviderSettingVolcengineTest {
    @Test
    fun volcengine_defaults_use_bigasr_sauc_resource_id() {
        val setting = ASRProviderSetting.Volcengine()

        assertEquals("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel", setting.websocketUrl)
        assertEquals("volc.bigasr.sauc.duration", setting.resourceId)
    }
}
