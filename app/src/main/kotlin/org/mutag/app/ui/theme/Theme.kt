package org.mutag.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import org.mutag.app.settings.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorSurface,
    primaryContainer = ColorPrimaryLight,
    onPrimaryContainer = ColorOnPrimaryLight,
    background = ColorBackground,
    surface = ColorSurface,
    surfaceVariant = ColorSurfaceVariant,
    onBackground = ColorTextPrimary,
    onSurface = ColorTextPrimary,
    onSurfaceVariant = ColorTextSecondary,
    outline = ColorBorder,
    outlineVariant = ColorDivider,
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorPrimaryLight,
    onPrimary = ColorOnPrimaryLight,
    primaryContainer = ColorPrimary,
    onPrimaryContainer = ColorPrimaryLight,
    background = ColorDarkBackground,
    surface = ColorDarkSurface,
    surfaceVariant = ColorDarkSurfaceVariant,
    onBackground = ColorDarkTextPrimary,
    onSurface = ColorDarkTextPrimary,
    onSurfaceVariant = ColorDarkTextSecondary,
    outline = ColorDarkBorder,
)

/**
 * MuTag 全局 Material 3 主题容器组件。
 *
 * @param themeMode 主题模式（系统/浅色/深色）
 * @param dynamicColor 是否启用 Android 12+ 动态壁纸取色
 * @param amoledBlack 是否启用深色模式下的纯黑背景
 * @param seedColor 自定义或预设种子色彩
 * @param content 主题作用域子组件树
 */
@Composable
fun MuTagTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    seedColor: Color = Color(0xFF6750A4),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val finalScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && amoledBlack) {
                dynamicScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                )
            } else {
                dynamicScheme
            }
        }

        else -> DynamicColorSchemeGenerator.createColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            amoledBlack = amoledBlack,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowInsetsControllerCompat(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = finalScheme,
        content = content,
    )
}
