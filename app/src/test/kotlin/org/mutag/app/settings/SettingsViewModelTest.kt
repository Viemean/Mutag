package org.mutag.app.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsViewModelTest {

    @Test
    fun setThemeModeUpdatesState() {
        val vm = SettingsViewModel()
        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
    }

    @Test
    fun setPreferredSourceUpdatesState() {
        val vm = SettingsViewModel()
        vm.setPreferredSource(MetadataSource.NETEASE)
        assertEquals(MetadataSource.NETEASE, vm.state.value.preferredSource)
    }

    @Test
    fun setArtistSeparatorUpdatesState() {
        val vm = SettingsViewModel()
        vm.setArtistSeparator("; ")
        assertEquals("; ", vm.state.value.artistSeparator)
    }

    @Test
    fun setThemeColorUpdatesState() {
        val vm = SettingsViewModel()
        vm.setThemeColor(0xFF386A20L, isCustom = false)
        assertEquals(0xFF386A20L, vm.state.value.themeColor)
        assertEquals(false, vm.state.value.isCustomThemeColor)

        vm.setThemeColor(0xFF00FF00L, isCustom = true)
        assertEquals(0xFF00FF00L, vm.state.value.themeColor)
        assertEquals(true, vm.state.value.isCustomThemeColor)
    }
}
