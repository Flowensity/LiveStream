package com.livestream.app

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.livestream.core.model.ThemeMode
import kotlinx.coroutines.launch
import com.livestream.core.ui.theme.LiveStreamTheme
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
import com.livestream.feature.channel.ChannelViewModel
import com.livestream.feature.home.HomeViewModel
import com.livestream.feature.home.ui.HomeScreen
import com.livestream.feature.player.PlayerViewModel
import com.livestream.feature.settings.SettingsViewModel
import com.livestream.playback.api.PlaybackOrchestrator
import com.livestream.source.api.SourceAdapterRegistry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var playbackOrchestrator: PlaybackOrchestrator

    @Inject
    lateinit var sourceAdapterRegistry: SourceAdapterRegistry

    private val homeViewModel: HomeViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val channelViewModel: ChannelViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // 手机端真·沉浸式 Edge-to-Edge 适配
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        lifecycleScope.launch {
            channelRepository.checkAndSeedDefaultChannels()
        }
        setContent {
            val settingsUiState by settingsViewModel.uiState.collectAsState()
            val isDarkTheme = when (settingsUiState.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                if (isLandscape) {
                    com.livestream.core.ui.util.SystemBarsUtil.setImmersiveFullscreen(window, true)
                }
            }

            LiveStreamTheme(darkTheme = isDarkTheme) {
                HomeScreen(
                    homeViewModel = homeViewModel,
                    playerViewModel = playerViewModel,
                    channelViewModel = channelViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}
