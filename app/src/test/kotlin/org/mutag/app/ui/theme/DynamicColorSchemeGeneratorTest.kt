package org.mutag.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicColorSchemeGeneratorTest {

    @Test
    fun testDefaultBaselineColorSchemeCreation() {
        val seed = Color(0xFF6750A4)
        val lightScheme = DynamicColorSchemeGenerator.createColorScheme(seed, isDark = false)
        val darkScheme = DynamicColorSchemeGenerator.createColorScheme(seed, isDark = true)

        assertNotNull(lightScheme.primary)
        assertNotNull(lightScheme.surface)
        assertNotNull(darkScheme.primary)
        assertNotNull(darkScheme.surface)
    }

    @Test
    fun testExtremelySaturatedSeedConvergence() {
        val neonGreen = Color(0xFF00FF00)
        val lightScheme = DynamicColorSchemeGenerator.createColorScheme(neonGreen, isDark = false)
        val darkScheme = DynamicColorSchemeGenerator.createColorScheme(neonGreen, isDark = true)

        assertNotNull(lightScheme.primary)
        assertNotNull(lightScheme.primaryContainer)
        assertNotNull(darkScheme.primary)
        assertNotNull(darkScheme.primaryContainer)

        val pureYellow = Color(0xFFFFFF00)
        val yellowLight = DynamicColorSchemeGenerator.createColorScheme(pureYellow, isDark = false)
        assertNotNull(yellowLight.primary)
        assertNotNull(yellowLight.onPrimary)
    }

    @Test
    fun testAmoledBlackOverwritesSurface() {
        val seed = Color(0xFF0061A4)
        val darkAmoledScheme = DynamicColorSchemeGenerator.createColorScheme(
            seedColor = seed,
            isDark = true,
            amoledBlack = true,
        )

        assertEquals(Color.Black, darkAmoledScheme.background)
        assertEquals(Color.Black, darkAmoledScheme.surface)
        assertEquals(Color.Black, darkAmoledScheme.surfaceContainerLowest)
    }

    @Test
    fun testNeutralColorToneMapping() {
        val darkGrey = Color(0xFF1E1E1E)
        val scheme = DynamicColorSchemeGenerator.createColorScheme(darkGrey, isDark = false)
        assertTrue(scheme.primary.alpha > 0f)
        assertTrue(scheme.background.alpha > 0f)
    }
}
