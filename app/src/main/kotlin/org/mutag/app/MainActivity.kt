package org.mutag.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import org.mutag.app.edit.EditScreen
import org.mutag.app.edit.EditViewModel
import org.mutag.app.main.MainScreen
import org.mutag.app.main.MainViewModel
import org.mutag.app.notification.MatchingNotificationHelper
import org.mutag.app.settings.AppSettingsManager
import org.mutag.app.settings.SettingsScreen
import org.mutag.app.settings.SettingsViewModel
import org.mutag.app.ui.theme.MuTagTheme
import org.mutag.core.tagger.storage.StorageAccessManager

/**
 * 应用程序主 Activity，承载权限请求与 Compose 导航根视图。
 */
class MainActivity : ComponentActivity() {

    private val openUncertainTrigger = mutableStateOf(false)
    private val testUpdateTrigger = mutableStateOf<org.mutag.app.update.AppUpdateInfo?>(null)

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // 系统权限设置返回回调
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.enableEdgeToEdge(window)

        checkAndRequestStoragePermission()
        checkOpenUncertainIntent(intent)
        checkTestUpdateIntent(intent)

        setContent {
            val settingsState by AppSettingsManager.settingsState.collectAsState()

            MuTagTheme(
                themeMode = settingsState.themeMode,
                dynamicColor = settingsState.dynamicColor,
                amoledBlack = settingsState.amoledBlack,
                seedColor = androidx.compose.ui.graphics.Color(settingsState.themeColor),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MuTagAppNavigation(
                        openUncertain = openUncertainTrigger.value,
                        testUpdateInfo = testUpdateTrigger.value,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkOpenUncertainIntent(intent)
        checkTestUpdateIntent(intent)
    }

    private fun checkOpenUncertainIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(MatchingNotificationHelper.EXTRA_OPEN_UNCERTAIN, false) == true) {
            openUncertainTrigger.value = true
        }
    }

    private fun checkTestUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("test_update", false) == true || intent?.getStringExtra("test_update") != null) {
            val version = intent.getStringExtra("version") ?: "1.1.0"
            val notes = intent.getStringExtra("notes") ?: "### 更新日志\n- 新增 GitHub Actions 自动构建支持\n- 新增版本在线检查与更新弹窗\n- 优化音频标签解析性能与稳定性"
            testUpdateTrigger.value = org.mutag.app.update.AppUpdateInfo(
                versionName = version,
                tag = "v$version",
                releaseNotes = notes,
                downloadUrl = "https://github.com/Viemean/Mutag/releases",
                publishDate = "2026-10-25",
                isNewer = true,
            )
        }
    }

    private fun checkAndRequestStoragePermission() {
        if (!StorageAccessManager.hasStoragePermission(this)) {
            val intent = StorageAccessManager.createStorageManagerIntent(this)
            if (intent != null) {
                storagePermissionLauncher.launch(intent)
            }
        }
    }
}

/**
 * 应用程序根级导航路由组件。
 *
 * @param openUncertain 是否直接展开存疑核对底栏
 * @param testUpdateInfo 测试触发的更新信息
 */
@Composable
fun MuTagAppNavigation(openUncertain: Boolean = false, testUpdateInfo: org.mutag.app.update.AppUpdateInfo? = null) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    LaunchedEffect(openUncertain) {
        if (openUncertain) {
            mainViewModel.openUncertainSheet()
        }
    }

    LaunchedEffect(testUpdateInfo) {
        if (testUpdateInfo != null) {
            mainViewModel.showMockUpdate(testUpdateInfo)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "main",
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
        },
    ) {
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToEdit = { rawPath ->
                    val encoded = URLEncoder.encode(rawPath, "UTF-8")
                    navController.navigate("edit/$encoded")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
            )
        }

        composable(
            route = "edit/{path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("path") ?: ""
            val decodedPath = URLDecoder.decode(encodedPath, "UTF-8")
            val editViewModel: EditViewModel = viewModel()

            LaunchedEffect(decodedPath) {
                editViewModel.loadFile(decodedPath)
            }

            EditScreen(
                viewModel = editViewModel,
                onNavigateBack = {
                    if (editViewModel.state.value.saveSuccess == true) {
                        mainViewModel.onTrackManuallySaved(decodedPath)
                    }
                    navController.popBackStack()
                },
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
