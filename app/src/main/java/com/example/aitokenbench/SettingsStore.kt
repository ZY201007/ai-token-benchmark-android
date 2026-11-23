package com.example.aitokenbench

import android.content.Context
import android.content.SharedPreferences

/** 使用 SharedPreferences 保存接口配置；API Key 仅存本机，不随结果上传 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mockMode: Boolean
        get() = prefs.getBoolean(KEY_MOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_MOCK, value).apply()

    var promptTemplate: String
        get() = prefs.getString(KEY_TEMPLATE, Pricing.DEFAULT_PROMPT_TEMPLATE)
            ?: Pricing.DEFAULT_PROMPT_TEMPLATE
        set(value) = prefs.edit().putString(KEY_TEMPLATE, value).apply()

    fun loadModels(): List<ModelConfig> = Pricing.defaultModels().map { defaults ->
        ModelConfig(
            id = defaults.id,
            displayName = defaults.displayName,
            baseUrl = prefs.getString("${defaults.id}_base", defaults.baseUrl) ?: defaults.baseUrl,
            apiKey = prefs.getString("${defaults.id}_key", "") ?: "",
            modelName = prefs.getString("${defaults.id}_model", defaults.modelName) ?: defaults.modelName,
            priceInputPerMillion = prefs.getFloat("${defaults.id}_pin", defaults.priceInputPerMillion.toFloat()).toDouble(),
            priceOutputPerMillion = prefs.getFloat("${defaults.id}_pout", defaults.priceOutputPerMillion.toFloat()).toDouble(),
            enabled = prefs.getBoolean("${defaults.id}_enabled", true)
        )
    }

    fun saveModels(models: List<ModelConfig>) {
        prefs.edit().apply {
            models.forEach { m ->
                putString("${m.id}_base", m.baseUrl)
                putString("${m.id}_key", m.apiKey)
                putString("${m.id}_model", m.modelName)
                putFloat("${m.id}_pin", m.priceInputPerMillion.toFloat())
                putFloat("${m.id}_pout", m.priceOutputPerMillion.toFloat())
                putBoolean("${m.id}_enabled", m.enabled)
            }
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "aitokenbench_settings"
        private const val KEY_MOCK = "mock_mode"
        private const val KEY_TEMPLATE = "prompt_template"
    }
}
