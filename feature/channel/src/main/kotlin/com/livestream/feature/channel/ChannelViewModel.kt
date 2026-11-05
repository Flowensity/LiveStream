package com.livestream.feature.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.domain.channel.repository.CategoryRepository
import com.livestream.domain.channel.repository.ChannelRepository
import com.livestream.domain.channel.usecase.SearchChannelsUseCase
import com.livestream.domain.channel.usecase.ToggleFavoriteUseCase
import com.livestream.playback.api.PlaybackOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val searchChannelsUseCase: SearchChannelsUseCase,
    private val playbackOrchestrator: PlaybackOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChannelUiState(
            categories = listOf(ChannelUiState.ALL_CATEGORY, ChannelUiState.FAVORITE_CATEGORY),
            selectedCategory = ChannelUiState.ALL_CATEGORY
        )
    )
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ChannelCategory>(ChannelUiState.ALL_CATEGORY)
    private val _searchQuery = MutableStateFlow("")

    val categories: StateFlow<List<ChannelCategory>> = categoryRepository.observeCategories()
        .map { dbCategories ->
            listOf(ChannelUiState.ALL_CATEGORY, ChannelUiState.FAVORITE_CATEGORY) + dbCategories
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = listOf(ChannelUiState.ALL_CATEGORY, ChannelUiState.FAVORITE_CATEGORY)
        )

    init {
        // 观察分类变化
        viewModelScope.launch {
            categories.collectLatest { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }

        // 观察活跃频道
        viewModelScope.launch {
            playbackOrchestrator.orchestratorState.collectLatest { orch ->
                _uiState.update { it.copy(activeChannelId = orch.currentChannel?.id) }
            }
        }

        // 观察频道列表与搜索过滤
        viewModelScope.launch {
            combine(_selectedCategory, _searchQuery) { cat, query ->
                Pair(cat, query)
            }.flatMapLatest { (category, query) ->
                if (query.isNotBlank()) {
                    searchChannelsUseCase(query)
                } else {
                    when (category.id) {
                        ChannelUiState.ALL_CATEGORY.id -> channelRepository.observeAllVisibleChannels()
                        ChannelUiState.FAVORITE_CATEGORY.id -> channelRepository.observeFavoriteChannels()
                        else -> channelRepository.observeChannelsByCategory(category.id)
                    }
                }
            }.collectLatest { chs ->
                _uiState.update { it.copy(channels = chs) }
            }
        }
    }

    fun selectCategory(category: ChannelCategory) {
        _selectedCategory.value = category
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun selectChannel(channel: Channel) {
        closeDrawer()
        viewModelScope.launch {
            playbackOrchestrator.playChannel(channel)
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            toggleFavoriteUseCase(channel.id, !channel.isFavorite)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openDrawer() {
        _uiState.update { it.copy(isDrawerOpen = true) }
    }

    fun closeDrawer() {
        _uiState.update { it.copy(isDrawerOpen = false) }
    }

    fun toggleDrawer() {
        _uiState.update { it.copy(isDrawerOpen = !it.isDrawerOpen) }
    }
}
