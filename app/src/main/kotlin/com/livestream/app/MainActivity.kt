package com.livestream.app

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import com.livestream.core.model.PlaybackStatus
import com.livestream.core.model.ThemeMode
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
    private var pausedByLifecycle = false
    private val isInPipModeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 手机端真·沉浸式 Edge-to-Edge 适配
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val settingsUiState by settingsViewModel.uiState.collectAsState()
            val isInPipMode by isInPipModeState
            val isDarkTheme = when (settingsUiState.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                if (isLandscape || isInPipMode) {
                    com.livestream.core.ui.util.SystemBarsUtil.setImmersiveFullscreen(window, true)
                }
            }

            LiveStreamTheme(darkTheme = isDarkTheme) {
                HomeScreen(
                    homeViewModel = homeViewModel,
                    playerViewModel = playerViewModel,
                    channelViewModel = channelViewModel,
                    settingsViewModel = settingsViewModel,
                    isInPipMode = isInPipMode
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfEligible()
    }

    private fun enterPipIfEligible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return

        val status = playbackOrchestrator.orchestratorState.value.status
        val isActivePlayback = status == PlaybackStatus.PLAYING ||
            status == PlaybackStatus.BUFFERING ||
            status == PlaybackStatus.READY
        if (!isActivePlayback) return

        runCatching {
            val metrics = playbackOrchestrator.orchestratorState.value.metrics
            val rational = if (metrics.videoWidth > 0 && metrics.videoHeight > 0) {
                Rational(metrics.videoWidth, metrics.videoHeight)
            } else {
                Rational(16, 9)
            }
            val safeRational = if (rational.toFloat() in 0.42f..2.38f) rational else Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(safeRational)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }
        isInPipModeState.value = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        if (pausedByLifecycle) {
            pausedByLifecycle = false
            playbackOrchestrator.resume()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            pausedByLifecycle = true
            playbackOrchestrator.pause()
        }
    }
}
