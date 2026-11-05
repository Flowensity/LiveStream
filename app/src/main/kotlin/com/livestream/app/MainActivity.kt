package com.livestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livestream.core.ui.theme.DarkBackground
import com.livestream.core.ui.theme.FocusHighlight
import com.livestream.core.ui.theme.LiveStreamTheme
import com.livestream.core.ui.theme.TextMuted
import com.livestream.core.ui.theme.TextPrimary
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.repository.UserPreferencesRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiveStreamTheme {
                MainPlaceholderScreen()
            }
        }
    }
}

@Composable
fun MainPlaceholderScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "LiveStream",
                color = FocusHighlight,
                fontSize = 32.sp,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "高性能多端直播应用架构基线已就绪",
                color = TextPrimary,
                fontSize = 18.sp,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Phase 1: Architecture Baseline Verified",
                color = TextMuted,
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
