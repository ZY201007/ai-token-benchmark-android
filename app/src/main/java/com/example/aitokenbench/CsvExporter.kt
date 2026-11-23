package com.example.aitokenbench

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CSV 导出：明细 + 按模型汇总。使用 UTF-8，逗号/引号/换行按 RFC4180 转义。 */
object CsvExporter {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** 明细 CSV：与 SQLite measurements 表一一对应 */
    fun detailCsv(list: List<Measurement>): String {
        val sb = StringBuilder()
        sb.append("id,task_index,model_id,model_name,input_tokens,output_tokens,")
            .append("latency_ms,cost_cny,mock,prompt_chars,timestamp,error\n")
        list.forEach { m ->
            sb.append(
                listOf(
                    m.id,
                    m.taskIndex,
                    m.modelId,
                    m.modelName,
                    m.inputTokens,
                    m.outputTokens,
                    m.latencyMs,
                    money(m.costCny),
                    if (m.mock) "mock" else "real",
                    m.promptChars,
                    timeFormat.format(Date(m.timestamp)),
                    m.error.orEmpty()
                ).joinToString(",") { escape(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    /** 按模型对比汇总：次数、token 合计、平均耗时、成本合计 */
    fun summaryCsv(list: List<Measurement>): String {
        val sb = StringBuilder()
        sb.append("model_id,model_name,runs,input_tokens_total,output_tokens_total,")
            .append("avg_latency_ms,cost_cny_total\n")

        val grouped = list.filter { it.error == null }.groupBy { it.modelId }
        grouped.values.forEach { rows ->
            val first = rows.first()
            val count = rows.size
            sb.append(
                listOf(
                    first.modelId,
                    first.modelName,
                    count,
                    rows.sumOf { it.inputTokens },
                    rows.sumOf { it.outputTokens },
                    rows.sumOf { it.latencyMs }.toDouble() / count,
                    money(rows.sumOf { it.costCny })
                ).joinToString(",") { escape(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    /** 汇总文本，用于界面上直接展示对比结果 */
    fun summaryText(list: List<Measurement>): String {
        val valid = list.filter { it.error == null }
        if (valid.isEmpty()) return "暂无可对比的数据（失败记录不计入汇总）。"
        val sb = StringBuilder()
        sb.append("模型\t次数\t输入\t输出\t平均耗时\t成本(元)\n")
        valid.groupBy { it.modelName }.values.forEach { rows ->
            val count = rows.size
            sb.append(rows.first().modelName).append('\t')
                .append(count).append('\t')
                .append(rows.sumOf { it.inputTokens }).append('\t')
                .append(rows.sumOf { it.outputTokens }).append('\t')
                .append(rows.sumOf { it.latencyMs } / count).append("ms\t")
                .append(money(rows.sumOf { it.costCny })).append('\n')
        }
        return sb.toString()
    }

    private fun money(value: Double): String =
        String.format(Locale.US, "%.4f", value)

    private fun escape(value: Any?): String {
        val s = value?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
    }
}
