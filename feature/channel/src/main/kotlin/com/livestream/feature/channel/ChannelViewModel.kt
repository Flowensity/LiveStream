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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val searchChannelsUseCase: SearchChannelsUseCase,
    private val playbackOrchestrator: PlaybackOrchestrator
) : ViewModel() {

    private val virtualCategories = listOf(
        ChannelUiState.ALL_CATEGORY,
        ChannelUiState.RECENT_CATEGORY,
        ChannelUiState.FAVORITE_CATEGORY
    )

    private val _uiState = MutableStateFlow(
        ChannelUiState(
            categories = virtualCategories,
            selectedCategory = ChannelUiState.ALL_CATEGORY
        )
    )
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ChannelCategory>(ChannelUiState.ALL_CATEGORY)
    private val _searchQuery = MutableStateFlow("")

    val categories: StateFlow<List<ChannelCategory>> = categoryRepository.observeCategories()
        .map { dbCategories ->
            virtualCategories + dbCategories
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = virtualCategories
        )

    init {
        // 观察分类变化（当选中的自定义分类被删除时自动回退至「全部频道」）
        viewModelScope.launch {
            categories.collectLatest { cats ->
                val currentSelected = _selectedCategory.value
                val validSelected = if (cats.any { it.id == currentSelected.id }) {
                    currentSelected
                } else {
                    ChannelUiState.ALL_CATEGORY
                }
                if (validSelected.id != currentSelected.id) {
                    _selectedCategory.value = validSelected
                }
                _uiState.update { it.copy(categories = cats, selectedCategory = validSelected) }
            }
        }

        // 观察活跃频道
        viewModelScope.launch {
            playbackOrchestrator.orchestratorState.collectLatest { orch ->
                _uiState.update { it.copy(activeChannelId = orch.currentChannel?.id) }
            }
        }

        // 观察频道列表与搜索过滤（对非空搜索词应用 200ms 防抖，清空搜索时立即响应）
        viewModelScope.launch {
            val debouncedQuery = _searchQuery.debounce { query ->
                if (query.isBlank()) 0L else 200L
            }
            combine(_selectedCategory, debouncedQuery) { cat, query ->
                Pair(cat, query)
            }.flatMapLatest { (category, query) ->
                if (query.isNotBlank()) {
                    searchChannelsUseCase(query)
                } else {
                    when (category.id) {
                        ChannelUiState.ALL_CATEGORY.id -> channelRepository.observeAllVisibleChannels()
                        ChannelUiState.RECENT_CATEGORY.id -> channelRepository.observeRecentHistoryChannels()
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
