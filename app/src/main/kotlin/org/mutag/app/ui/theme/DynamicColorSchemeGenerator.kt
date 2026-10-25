package org.mutag.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Material 3 动态色彩生成器。
 * 基于 CIELCH 色彩空间，将种子颜色转换为符合无障碍对比度规格的深浅配色方案。
 */
object DynamicColorSchemeGenerator {

    /**
     * 根据种子颜色生成对应的 Material 3 ColorScheme。
     *
     * @param seedColor 基础种子颜色
     * @param isDark 是否为深色模式
     * @param amoledBlack 深色模式下是否使用纯黑底色
     * @return 完整的 M3 配色方案
     */
    fun createColorScheme(seedColor: Color, isDark: Boolean, amoledBlack: Boolean = false): ColorScheme {
        val argb = seedColor.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF

        val lab = DoubleArray(3)
        rgbToLab(r, g, b, lab)

        val origA = lab[1]
        val origB = lab[2]
        val origChroma = hypot(origA, origB)
        var hueDegrees = Math.toDegrees(atan2(origB, origA))
        if (hueDegrees < 0) {
            hueDegrees += 360.0
        }

        val primaryChroma = if (origChroma < 4.0) 0.0 else origChroma.coerceIn(36.0, 48.0)
        val secondaryChroma = if (origChroma < 4.0) 0.0 else 16.0
        val tertiaryChroma = if (origChroma < 4.0) 0.0 else 24.0
        val tertiaryHue = (hueDegrees + 60.0) % 360.0
        val neutralChroma = if (origChroma < 4.0) 0.0 else 4.0
        val neutralVariantChroma = if (origChroma < 4.0) 0.0 else 8.0

        fun toneColor(hue: Double, chroma: Double, tone: Double): Color {
            val rad = Math.toRadians(hue)
            val a = chroma * cos(rad)
            val b = chroma * sin(rad)
            val colorInt = labToRgb(tone.coerceIn(0.0, 100.0), a, b)
            return Color(colorInt)
        }

        return if (isDark) {
            val baseDark = darkColorScheme(
                primary = toneColor(hueDegrees, primaryChroma, 80.0),
                onPrimary = toneColor(hueDegrees, primaryChroma, 20.0),
                primaryContainer = toneColor(hueDegrees, primaryChroma, 30.0),
                onPrimaryContainer = toneColor(hueDegrees, primaryChroma, 90.0),
                secondary = toneColor(hueDegrees, secondaryChroma, 80.0),
                onSecondary = toneColor(hueDegrees, secondaryChroma, 20.0),
                secondaryContainer = toneColor(hueDegrees, secondaryChroma, 30.0),
                onSecondaryContainer = toneColor(hueDegrees, secondaryChroma, 90.0),
                tertiary = toneColor(tertiaryHue, tertiaryChroma, 80.0),
                onTertiary = toneColor(tertiaryHue, tertiaryChroma, 20.0),
                tertiaryContainer = toneColor(tertiaryHue, tertiaryChroma, 30.0),
                onTertiaryContainer = toneColor(tertiaryHue, tertiaryChroma, 90.0),
                background = toneColor(hueDegrees, neutralChroma, 10.0),
                onBackground = toneColor(hueDegrees, neutralChroma, 90.0),
                surface = toneColor(hueDegrees, neutralChroma, 10.0),
                onSurface = toneColor(hueDegrees, neutralChroma, 90.0),
                surfaceVariant = toneColor(hueDegrees, neutralVariantChroma, 30.0),
                onSurfaceVariant = toneColor(hueDegrees, neutralVariantChroma, 80.0),
                surfaceContainerLowest = toneColor(hueDegrees, neutralChroma, 4.0),
                surfaceContainerLow = toneColor(hueDegrees, neutralChroma, 10.0),
                surfaceContainer = toneColor(hueDegrees, neutralChroma, 12.0),
                surfaceContainerHigh = toneColor(hueDegrees, neutralChroma, 17.0),
                surfaceContainerHighest = toneColor(hueDegrees, neutralChroma, 22.0),
                outline = toneColor(hueDegrees, neutralVariantChroma, 60.0),
                outlineVariant = toneColor(hueDegrees, neutralVariantChroma, 30.0),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
            )
            if (amoledBlack) {
                baseDark.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = toneColor(hueDegrees, neutralChroma, 5.0),
                )
            } else {
                baseDark
            }
        } else {
            lightColorScheme(
                primary = toneColor(hueDegrees, primaryChroma, 40.0),
                onPrimary = toneColor(hueDegrees, primaryChroma, 100.0),
                primaryContainer = toneColor(hueDegrees, primaryChroma, 90.0),
                onPrimaryContainer = toneColor(hueDegrees, primaryChroma, 10.0),
                secondary = toneColor(hueDegrees, secondaryChroma, 40.0),
                onSecondary = toneColor(hueDegrees, secondaryChroma, 100.0),
                secondaryContainer = toneColor(hueDegrees, secondaryChroma, 90.0),
                onSecondaryContainer = toneColor(hueDegrees, secondaryChroma, 10.0),
                tertiary = toneColor(tertiaryHue, tertiaryChroma, 40.0),
                onTertiary = toneColor(tertiaryHue, tertiaryChroma, 100.0),
                tertiaryContainer = toneColor(tertiaryHue, tertiaryChroma, 90.0),
                onTertiaryContainer = toneColor(tertiaryHue, tertiaryChroma, 10.0),
                background = toneColor(hueDegrees, neutralChroma, 99.0),
                onBackground = toneColor(hueDegrees, neutralChroma, 10.0),
                surface = toneColor(hueDegrees, neutralChroma, 99.0),
                onSurface = toneColor(hueDegrees, neutralChroma, 10.0),
                surfaceVariant = toneColor(hueDegrees, neutralVariantChroma, 90.0),
                onSurfaceVariant = toneColor(hueDegrees, neutralVariantChroma, 30.0),
                surfaceContainerLowest = toneColor(hueDegrees, neutralChroma, 100.0),
                surfaceContainerLow = toneColor(hueDegrees, neutralChroma, 96.0),
                surfaceContainer = toneColor(hueDegrees, neutralChroma, 94.0),
                surfaceContainerHigh = toneColor(hueDegrees, neutralChroma, 92.0),
                surfaceContainerHighest = toneColor(hueDegrees, neutralChroma, 90.0),
                outline = toneColor(hueDegrees, neutralVariantChroma, 50.0),
                outlineVariant = toneColor(hueDegrees, neutralVariantChroma, 80.0),
                error = Color(0xFFBA1A1A),
                onError = Color(0xFFFFFFFF),
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002),
            )
        }
    }

    private fun srgbToLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else (c + 0.055).div(1.055).pow(2.4)

    private fun linearToSrgb(c: Double): Double = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

    private fun rgbToLab(r: Int, g: Int, b: Int, outLab: DoubleArray) {
        val lr = srgbToLinear(r / 255.0)
        val lg = srgbToLinear(g / 255.0)
        val lb = srgbToLinear(b / 255.0)

        val x = (lr * 0.4124 + lg * 0.3576 + lb * 0.1805) / 0.95047
        val y = (lr * 0.2126 + lg * 0.7152 + lb * 0.0722) / 1.00000
        val z = (lr * 0.0193 + lg * 0.1192 + lb * 0.9505) / 1.08883

        fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else (7.787 * t) + (16.0 / 116.0)

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        outLab[0] = (116.0 * fy - 16.0).coerceIn(0.0, 100.0)
        outLab[1] = 500.0 * (fx - fy)
        outLab[2] = 200.0 * (fy - fz)
    }

    private fun labToRgb(l: Double, a: Double, b: Double): Int {
        val fy = (l + 16.0) / 116.0
        val fx = a / 500.0 + fy
        val fz = fy - b / 200.0

        fun finv(t: Double): Double {
            val t3 = t * t * t
            return if (t3 > 0.008856) t3 else (t - 16.0 / 116.0) / 7.787
        }

        val x = finv(fx) * 0.95047
        val y = finv(fy) * 1.00000
        val z = finv(fz) * 1.08883

        val rLin = x * 3.2406 + y * -1.5372 + z * -0.4986
        val gLin = x * -0.9689 + y * 1.8758 + z * 0.0415
        val bLin = x * 0.0557 + y * -0.2040 + z * 1.0570

        val r = (linearToSrgb(rLin).coerceIn(0.0, 1.0) * 255.0).roundToInt()
        val g = (linearToSrgb(gLin).coerceIn(0.0, 1.0) * 255.0).roundToInt()
        val bVal = (linearToSrgb(bLin).coerceIn(0.0, 1.0) * 255.0).roundToInt()

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bVal
    }
}
