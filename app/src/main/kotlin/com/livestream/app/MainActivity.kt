package com.livestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
