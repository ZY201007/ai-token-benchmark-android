package com.example.aitokenbench

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.random.Random

/**
 * OpenAI 兼容的 chat/completions 调用封装。
 * - mockMode=true：完全离线，用校准器估算 token 并模拟耗时，便于无 Key 演示。
 * - mockMode=false：真实 HTTP 调用，从 usage 字段读取输入/输出 token。
 *
 * 本类只负责「调用并返回结果」，不上传任何测评数据到第三方。
 */
class ApiClient {

    data class CallResult(
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val latencyMs: Long = 0L,
        val error: String? = null
    )

    fun call(
        model: ModelConfig,
        prompt: String,
        mockMode: Boolean,
        calibrator: Calibrator
    ): CallResult {
        val start = System.currentTimeMillis()
        return if (mockMode) callMock(start, prompt, calibrator) else callReal(start, model, prompt)
    }

    private fun callMock(start: Long, prompt: String, calibrator: Calibrator): CallResult {
        // 模拟 150~600ms 的网络与推理耗时
        Thread.sleep(150L + Random.nextLong(450L))
        val inputTokens = calibrator.estimate(prompt.length)
        val outputTokens = max(16, (inputTokens * 0.35 + 12).toInt())
        return CallResult(inputTokens, outputTokens, System.currentTimeMillis() - start)
    }

    private fun callReal(start: Long, model: ModelConfig, prompt: String): CallResult {
        return try {
            val endpoint = model.baseUrl.trimEnd('/') + "/chat/completions"
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer ${model.apiKey}")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("model", model.modelName)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", prompt)
                    )
                )
                put("temperature", 0.2)
                put("max_tokens", 512)
                put("stream", false)
            }.toString()

            conn.outputStream.use { os -> os.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            conn.disconnect()

            val latency = System.currentTimeMillis() - start
            if (code !in 200..299) {
                return CallResult(0, 0, latency, "HTTP $code: ${text.take(200)}")
            }

            val usage = JSONObject(text).optJSONObject("usage")
            val inputTokens = usage?.optInt("prompt_tokens", 0) ?: 0
            val outputTokens = usage?.optInt("completion_tokens", 0) ?: 0
            CallResult(inputTokens, outputTokens, latency)
        } catch (e: Exception) {
            CallResult(0, 0, System.currentTimeMillis() - start, e.message ?: "未知错误")
        }
    }
}
