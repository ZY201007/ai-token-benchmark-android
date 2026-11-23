package com.example.aitokenbench

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/** 接口与价格设置界面：控件按模型动态生成，避免臃肿的 XML */
class SettingsActivity : AppCompatActivity() {

    private class ModelViews(
        val enabled: CheckBox,
        val base: EditText,
        val key: EditText,
        val name: EditText,
        val priceIn: EditText,
        val priceOut: EditText
    )

    private lateinit var settings: SettingsStore
    private lateinit var calibrator: Calibrator
    private lateinit var container: LinearLayout
    private lateinit var mockSwitch: SwitchCompat
    private lateinit var templateEdit: EditText
    private lateinit var modelViews: MutableList<ModelViews>
    private lateinit var models: List<ModelConfig>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = SettingsStore(this)
        calibrator = Calibrator()
        models = settings.loadModels()
        modelViews = mutableListOf()

        container = findViewById(R.id.settings_container)
        findViewById<TextView>(R.id.tv_calibration).text = calibrator.report()

        // Mock 模式开关
        mockSwitch = SwitchCompat(this).apply {
            isChecked = settings.mockMode
            text = getString(R.string.label_mock_mode)
            textSize = 14f
        }
        container.addView(mockSwitch, lp(0))

        // 统一 prompt 模板
        addLabel(getString(R.string.label_prompt_template), top = 16)
        templateEdit = makeEditText(
            settings.promptTemplate,
            "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        templateEdit.minLines = 3
        templateEdit.gravity = Gravity.TOP or Gravity.START
        container.addView(templateEdit, lp(4))

        // 四个模型的配置区
        models.forEach { model ->
            addLabel("── ${model.displayName} ──", top = 18)
            val enabled = CheckBox(this).apply {
                isChecked = model.enabled
                text = getString(R.string.label_enabled)
                textSize = 14f
            }
            container.addView(enabled, lp(4))

            val base = makeEditText(model.baseUrl, getString(R.string.label_base_url))
            container.addView(base, lp(4))

            val key = makeEditText(
                model.apiKey,
                getString(R.string.label_api_key),
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            )
            container.addView(key, lp(4))

            val name = makeEditText(model.modelName, getString(R.string.label_model_name))
            container.addView(name, lp(4))

            val priceIn = makeEditText(
                model.priceInputPerMillion.toString(),
                getString(R.string.label_price_in),
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            )
            container.addView(priceIn, lp(4))

            val priceOut = makeEditText(
                model.priceOutputPerMillion.toString(),
                getString(R.string.label_price_out),
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            )
            container.addView(priceOut, lp(4))

            modelViews.add(ModelViews(enabled, base, key, name, priceIn, priceOut))
        }

        findViewById<android.widget.Button>(R.id.btn_save).setOnClickListener { save() }
        findViewById<android.widget.Button>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun save() {
        settings.mockMode = mockSwitch.isChecked
        settings.promptTemplate = templateEdit.text.toString()

        val updated = models.mapIndexed { index, model ->
            val v = modelViews[index]
            model.copy(
                enabled = v.enabled.isChecked,
                baseUrl = v.base.text.toString().trim(),
                apiKey = v.key.text.toString().trim(),
                modelName = v.name.text.toString().trim(),
                priceInputPerMillion = v.priceIn.text.toString().toDoubleOrNull()
                    ?: model.priceInputPerMillion,
                priceOutputPerMillion = v.priceOut.text.toString().toDoubleOrNull()
                    ?: model.priceOutputPerMillion
            )
        }
        settings.saveModels(updated)
        Toast.makeText(this, "设置已保存到本机", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun addLabel(text: String, top: Int) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, theme))
        }
        container.addView(tv, lp(top))
    }

    private fun makeEditText(text: String, hint: String, inputType: Int): EditText =
        EditText(this).apply {
            setText(text)
            this.hint = hint
            this.inputType = inputType
            textSize = 14f
            setBackgroundColor(resources.getColor(R.color.surface, theme))
            setPadding(12, 12, 12, 12)
        }

    private fun lp(topMarginDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (topMarginDp * resources.displayMetrics.density).toInt()
        }
}
