package com.example.aitokenbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校准模块的纯 JVM 单元测试。
 * Calibrator 只依赖 kotlin.math 与 JDK，不需要 Android 运行时。
 */
class CalibratorTest {

    @Test
    fun `内置样本为100条`() {
        assertEquals(100, Calibrator.SAMPLE_COUNT)
        assertEquals(100, Calibrator.SAMPLES.size)
    }

    @Test
    fun `基线固定系数为1点23`() {
        assertEquals(1.23, Calibrator.NAIVE_RATIO, 1e-9)
    }

    @Test
    fun `基线MAPE约百分之15`() {
        val calibrator = Calibrator()
        // 内置 100 样本精确值约 15.17%，允许 ±0.5% 容差。
        assertEquals(15.0, calibrator.baselineMape(), 0.5)
    }

    @Test
    fun `校准后MAPE小于百分之5且优于基线`() {
        val calibrator = Calibrator()
        // 内置 100 样本精确值约 2.62%。
        assertTrue(
            "校准后 MAPE 应 < 5%，实际为 ${calibrator.calibratedMape()}",
            calibrator.calibratedMape() < 5.0
        )
        assertTrue(
            "校准后 MAPE 应低于基线 MAPE",
            calibrator.calibratedMape() < calibrator.baselineMape()
        )
    }

    @Test
    fun `线性拟合参数与样本量一致`() {
        val calibrator = Calibrator()
        // slope ≈ 1.4536、intercept ≈ -0.4770，由最小二乘在 SAMPLES 上拟合得到。
        assertEquals(1.4536, calibrator.slope, 1e-3)
        assertEquals(-0.4770, calibrator.intercept, 1e-3)
    }
}
