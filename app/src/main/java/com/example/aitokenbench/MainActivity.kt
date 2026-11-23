package com.example.aitokenbench

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var calibrator: Calibrator
    private lateinit var apiClient: ApiClient
    private lateinit var repository: MeasurementRepository
    private lateinit var queue: BatchQueue

    private lateinit var etContent: EditText
    private lateinit var etTaskCount: EditText
    private lateinit var btnStart: Button
    private lateinit var tvProgress: TextView
    private lateinit var pbProgress: ProgressBar
    private lateinit var tvResults: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvCalibration: TextView
    private lateinit var modelContainer: LinearLayout
    private lateinit var modelChecks: MutableList<CheckBox>

    /**  SAF：让用户自行选择 CSV 保存位置，应用不访问其他文件  */
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val csv = CsvExporter.detailCsv(repository.all())
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(csv.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "CSV 已导出到您选择的位置", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsStore(this)
        calibrator = Calibrator()
        apiClient = ApiClient()
        repository = MeasurementRepository(this)
        queue = BatchQueue()

        etContent = findViewById(R.id.et_content)
        etTaskCount = findViewById(R.id.et_task_count)
        btnStart = findViewById(R.id.btn_start)
        tvProgress = findViewById(R.id.tv_progress)
        pbProgress = findViewById(R.id.pb_progress)
        tvResults = findViewById(R.id.tv_results)
        tvMode = findViewById(R.id.tv_mode)
        tvCalibration = findViewById(R.id.tv_calibration)
        modelContainer = findViewById(R.id.model_container)

        tvCalibration.text = calibrator.report()

        btnStart.setOnClickListener { startMeasurement() }
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btn_export_csv).setOnClickListener { launchExport() }
    }

    override fun onResume() {
        super.onResume()
        tvMode.text = getString(
            if (settings.mockMode) R.string.mode_mock else R.string.mode_real
        )
        // 校准报告有独立展示区，不再污染实时结果区，也不会每次返回都重复追加
        tvCalibration.text = calibrator.report()
        // 设置页可能改动了模型启用状态，返回时重建勾选框，避免旧勾选反向覆盖设置
        buildModelChecks()
    }

    /** 依据设置页保存的四家模型动态生成勾选框，让「选择模型」在主界面即可直接操作 */
    private fun buildModelChecks() {
        modelChecks = mutableListOf()
        modelContainer.removeAllViews()
        settings.loadModels().forEach { model ->
            val check = CheckBox(this).apply {
                isChecked = model.enabled
                text = "${model.displayName}（${model.modelName}）"
                textSize = 14f
            }
            modelContainer.addView(check)
            modelChecks.add(check)
        }
    }

    private fun startMeasurement() {
        val content = etContent.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请先输入工作内容", Toast.LENGTH_SHORT).show()
            return
        }

        val groupCount = (etTaskCount.text.toString().toIntOrNull() ?: 1)
            .coerceIn(1, BatchQueue.MAX_GROUPS)
        etTaskCount.setText(groupCount.toString())

        val allModels = settings.loadModels()
        if (allModels.isEmpty()) {
            Toast.makeText(this, "请到设置中至少启用一个模型", Toast.LENGTH_SHORT).show()
            return
        }
        val models = allModels.filterIndexed { index, _ -> modelChecks[index].isChecked }
        if (models.isEmpty()) {
            Toast.makeText(this, "请至少勾选一个参与测评的模型", Toast.LENGTH_SHORT).show()
            return
        }
        // 主界面的勾选结果同步回设置，保证主界面与设置页状态一致
        settings.saveModels(allModels.mapIndexed { index, model ->
            model.copy(enabled = modelChecks[index].isChecked)
        })

        val taskCount = queue.enqueueDerived(content, groupCount)
        val total = taskCount * models.size
        pbProgress.max = total
        pbProgress.progress = 0
        btnStart.isEnabled = false
        tvResults.text = "开始测评：$taskCount 组 × ${models.size} 个模型 = $total 次调用\n"
        val template = settings.promptTemplate
        val mock = settings.mockMode

        lifecycleScope.launch(Dispatchers.IO) {
            var done = 0
            var success = 0
            try {
                while (!queue.isEmpty) {
                    val task = queue.poll() ?: break
                    for (model in models) {
                        val prompt = Pricing.wrap(template, task.content)
                        val result = apiClient.call(model, prompt, mock, calibrator)
                        val cost = if (result.error == null) {
                            Pricing.costCny(model, result.inputTokens, result.outputTokens)
                        } else 0.0

                        repository.insert(
                            Measurement(
                                taskIndex = task.index,
                                modelId = model.id,
                                modelName = model.displayName,
                                inputTokens = result.inputTokens,
                                outputTokens = result.outputTokens,
                                latencyMs = result.latencyMs,
                                costCny = cost,
                                mock = mock,
                                promptChars = prompt.length,
                                timestamp = System.currentTimeMillis(),
                                error = result.error
                            )
                        )

                        if (result.error == null) success++
                        done++
                        val line = buildLine(task.index, model.displayName, result, cost)
                        withContext(Dispatchers.Main) {
                            pbProgress.progress = done
                            tvProgress.text = "进度：$done / $total"
                            tvResults.append(line)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    tvProgress.text = "完成：$done / $total（成功 $success）"
                    Toast.makeText(
                        this@MainActivity,
                        "测评完成，成功 $success / $total 条已存入本机数据库",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvProgress.text = "测评中断：${e.message ?: "未知错误"}"
                    Toast.makeText(
                        this@MainActivity,
                        "测评失败：${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                queue.clear()
                // 任何情况下都恢复「开始」按钮，避免异常后按钮永久禁用、界面卡死
                withContext(Dispatchers.Main) {
                    btnStart.isEnabled = true
                }
            }
        }
    }

    private fun launchExport() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // CreateDocument 契约的入参是「建议文件名」字符串，不是 Intent
        exportLauncher.launch("token_detail_$stamp.csv")
    }

    private fun buildLine(
        taskIndex: Int,
        modelName: String,
        result: ApiClient.CallResult,
        cost: Double
    ): String {
        val costText = String.format(Locale.US, "%.4f", cost)
        return if (result.error == null) {
            "组$taskIndex $modelName | in=${result.inputTokens} out=${result.outputTokens} " +
                "| ${result.latencyMs}ms | ¥$costText\n"
        } else {
            "组$taskIndex $modelName | 失败：${result.error.take(60)}\n"
        }
    }
}
