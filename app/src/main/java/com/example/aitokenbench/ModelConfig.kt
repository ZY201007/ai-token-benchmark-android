package com.example.aitokenbench

/**
 * 单个模型提供方的接入配置。
 * 四家服务均使用 OpenAI 兼容的 /chat/completions 协议，仅 baseUrl / modelName 不同。
 */
data class ModelConfig(
    val id: String,
    val displayName: String,
    var baseUrl: String,
    var apiKey: String,
    var modelName: String,
    var priceInputPerMillion: Double,
    var priceOutputPerMillion: Double,
    var enabled: Boolean = true
)

/**
 * 统一 prompt 模板与示例单价。
 * 单价仅用于演示成本折算，务必按各官网最新价格更新（见 README）。
 */
object Pricing {
    const val PRICE_NOTE = "示例单价，需按官网更新（单位：元 / 百万 token）"

    /** {content} 为占位符，运行时替换为用户输入的工作内容 */
    const val DEFAULT_PROMPT_TEMPLATE =
        "请评估完成以下工作内容所需的步骤，并给出简要执行方案。\n" +
            "要求：只输出方案本身，不要解释你的推理过程。\n\n" +
            "工作内容：\n{content}"

    fun wrap(template: String, content: String): String =
        if (template.contains("{content}")) template.replace("{content}", content)
        else "$template\n\n$content"

    /** 每次调用返回全新实例，避免设置界面的修改污染默认值 */
    fun defaultModels(): List<ModelConfig> = listOf(
        ModelConfig(
            id = "openai",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "",
            modelName = "gpt-4o-mini",
            priceInputPerMillion = 1.00,
            priceOutputPerMillion = 4.00
        ),
        ModelConfig(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "",
            modelName = "deepseek-chat",
            priceInputPerMillion = 1.00,
            priceOutputPerMillion = 2.00
        ),
        ModelConfig(
            id = "qwen",
            displayName = "通义千问",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            apiKey = "",
            modelName = "qwen-plus",
            priceInputPerMillion = 0.80,
            priceOutputPerMillion = 2.00
        ),
        ModelConfig(
            id = "doubao",
            displayName = "豆包",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            apiKey = "",
            modelName = "doubao-pro-32k",
            priceInputPerMillion = 0.80,
            priceOutputPerMillion = 2.00
        )
    )

    /** 成本 = 输入 token × 输入单价 + 输出 token × 输出单价（单位：元） */
    fun costCny(model: ModelConfig, inputTokens: Int, outputTokens: Int): Double =
        inputTokens / 1_000_000.0 * model.priceInputPerMillion +
            outputTokens / 1_000_000.0 * model.priceOutputPerMillion
}
