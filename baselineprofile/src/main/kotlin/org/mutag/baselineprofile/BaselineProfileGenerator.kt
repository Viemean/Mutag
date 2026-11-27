package org.mutag.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 MuTag 核心启动与首页滚动路径的 Baseline Profile。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "org.mutag.app",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // 若首次启动跳转到系统存储权限页，按返回键回到主界面继续采集
            if (!device.wait(Until.hasObject(By.pkg("org.mutag.app")), 3_000)) {
                device.pressBack()
                device.wait(Until.hasObject(By.pkg("org.mutag.app")), 3_000)
            }
            device.waitForIdle()

            val width = device.displayWidth
            val height = device.displayHeight
            val centerX = width / 2

            // 上下滑动主列表
            device.swipe(centerX, (height * 0.75).toInt(), centerX, (height * 0.25).toInt(), 20)
            device.waitForIdle()
            device.swipe(centerX, (height * 0.25).toInt(), centerX, (height * 0.75).toInt(), 20)
            device.waitForIdle()

            // 左右滑动切换 Tab Pager
            device.swipe((width * 0.85).toInt(), height / 2, (width * 0.15).toInt(), height / 2, 15)
            device.waitForIdle()
            device.swipe((width * 0.15).toInt(), height / 2, (width * 0.85).toInt(), height / 2, 15)
            device.waitForIdle()
        }
    }
}
