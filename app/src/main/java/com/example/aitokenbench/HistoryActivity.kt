package com.example.aitokenbench

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 历史明细、按模型对比与 CSV 导出（Storage Access Framework，仅写本机/用户选择的位置） */
class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val KIND_DETAIL = "detail"
        private const val KIND_SUMMARY = "summary"
    }

    private lateinit var repository: MeasurementRepository
    private lateinit var lvHistory: ListView
    private lateinit var tvCompare: TextView
    private lateinit var tvEmpty: TextView
    private var exportKind: String = KIND_DETAIL

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val all = repository.all()
                val csv = if (exportKind == KIND_SUMMARY) {
                    CsvExporter.summaryCsv(all)
                } else {
                    CsvExporter.detailCsv(all)
                }
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(csv.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "CSV 已导出", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        repository = MeasurementRepository(this)
        lvHistory = findViewById(R.id.lv_history)
        tvCompare = findViewById(R.id.tv_compare)
        tvEmpty = findViewById(R.id.tv_empty)

        findViewById<Button>(R.id.btn_compare).setOnClickListener {
            tvCompare.text = CsvExporter.summaryText(repository.all())
        }
        findViewById<Button>(R.id.btn_export).setOnClickListener {
            exportKind = KIND_DETAIL
            launchExport("token_detail")
        }
        findViewById<Button>(R.id.btn_export_summary).setOnClickListener {
            exportKind = KIND_SUMMARY
            launchExport("token_summary")
        }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { confirmClear() }
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val data = repository.recent(200)
        tvEmpty.visibility = if (data.isEmpty()) TextView.VISIBLE else TextView.GONE
        val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val lines = data.map { m ->
            val cost = String.format(Locale.US, "%.4f", m.costCny)
            val mode = if (m.mock) "mock" else "real"
            val err = m.error?.let { " 失败:${it.take(30)}" }.orEmpty()
            "#${m.id} 组${m.taskIndex} ${m.modelName} | in=${m.inputTokens} out=${m.outputTokens} | " +
                "${m.latencyMs}ms ¥$cost $mode ${timeFormat.format(Date(m.timestamp))}$err"
        }
        lvHistory.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lines)
        tvCompare.text = CsvExporter.summaryText(repository.all())
    }

    private fun launchExport(prefix: String) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // CreateDocument 契约入参是「建议文件名」字符串，传 Intent 会导致类型不匹配/导出失效
        exportLauncher.launch("${prefix}_$stamp.csv")
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("清空数据")
            .setMessage("确定删除本机数据库中所有测评记录？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                repository.clear()
                refresh()
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
