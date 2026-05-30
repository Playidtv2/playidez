package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.ChannelItem
import com.example.data.model.Playlist
import com.example.data.model.PlaylistError
import com.example.data.repository.IptvRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IptvRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = IptvRepository(
            database.playlistDao(),
            database.channelDao(),
            database.playlistErrorDao()
        )
        
        // Load fallback demo channels automatically on first start so they immediately have channels to play!
        viewModelScope.launch {
            repository.loadFallbackDemoStreams()
        }
    }

    // Tab state (super category): "TV" (ทีวี), "Movie" (หนัง), "Series" (ซีรีย์)
    private val _currentTab = MutableStateFlow("TV")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Playlist state
    private val _selectedPlaylistId = MutableStateFlow<Int?>(null) // null = all playlists
    val selectedPlaylistId: StateFlow<Int?> = _selectedPlaylistId.asStateFlow()

    // Category filter state
    private val _selectedCategory = MutableStateFlow<String?>(null) // null = all categories
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // Favorites only state
    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    // Playlist load state
    private val _playlistLoadingStatus = MutableStateFlow<PlaylistLoadStatus>(PlaylistLoadStatus.Idle)
    val playlistLoadingStatus: StateFlow<PlaylistLoadStatus> = _playlistLoadingStatus.asStateFlow()

    // Current playing channel state
    private val _currentPlayingChannel = MutableStateFlow<ChannelItem?>(null)
    val currentPlayingChannel: StateFlow<ChannelItem?> = _currentPlayingChannel.asStateFlow()

    // Repaired success feedback triggers
    private val _actionFeedback = MutableSharedFlow<String>()
    val actionFeedback: SharedFlow<String> = _actionFeedback.asSharedFlow()

    // Fetch lists of ALL playlists
    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fetch all categories for currently selected SuperCategory (Tab)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<String>> = combine(
        _currentTab,
        _selectedPlaylistId
    ) { currentTab, playlistId ->
        Pair(currentTab, playlistId)
    }.flatMapLatest { (currentTab, playlistId) ->
        repository.getCategoriesByFilter(currentTab, playlistId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter channels based on search query, playlistId, category, supercategory, and favorites
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredChannels: StateFlow<List<ChannelItem>> = combine(
        _currentTab,
        _selectedPlaylistId,
        _selectedCategory,
        _showFavoritesOnly,
        _searchQuery
    ) { tab, playlistId, category, showFavs, query ->
        IptvFilter(tab, playlistId, category, query, showFavs)
    }.flatMapLatest { filter ->
        repository.getFilteredChannels(
            tab = filter.tab,
            playlistId = filter.playlistId,
            category = filter.category,
            showFavs = filter.showFavs,
            query = filter.query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Watch for errors matching the currently selected playlist
    val currentPlaylistErrors: StateFlow<List<PlaylistError>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id != null) repository.getErrorsByPlaylist(id) else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Methods
    fun setTab(tab: String) {
        _currentTab.value = tab
        _selectedCategory.value = null // reset category filter
    }

    fun setQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectPlaylist(id: Int?) {
        _selectedPlaylistId.value = id
        _selectedCategory.value = null
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleFavoritesOnly() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    fun toggleChannelFavorite(channel: ChannelItem) {
        viewModelScope.launch {
            repository.updateFavorite(channel.id, !channel.isFavorite)
            // If the currently playing channel favorite status changes, sync it
            if (_currentPlayingChannel.value?.id == channel.id) {
                _currentPlayingChannel.value = channel.copy(isFavorite = !channel.isFavorite)
            }
        }
    }

    fun playChannel(channel: ChannelItem?) {
        _currentPlayingChannel.value = channel
    }

    /**
     * Add Playlist using standard URL
     */
    fun addM3uPlaylist(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) {
            viewModelScope.launch {
                _actionFeedback.emit("กรุณากรอกข้อมูลให้ครบถ้วน")
            }
            return
        }

        viewModelScope.launch {
            _playlistLoadingStatus.value = PlaylistLoadStatus.Loading("กำลังดาวน์โหลดและวิเคราะห์ไฟล์...")
            val result = repository.addPlaylist(name, url, "m3u")
            if (result.isSuccess) {
                val newId = result.getOrNull()
                _selectedPlaylistId.value = newId
                _selectedCategory.value = null
                _playlistLoadingStatus.value = PlaylistLoadStatus.Success("นำเข้าเพลย์ลิสต์สำเร็จ!")
                _actionFeedback.emit("นำเข้าข้อมูลสำเร็จ!")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _playlistLoadingStatus.value = PlaylistLoadStatus.Error(errorMsg)
                _actionFeedback.emit("การนำเข้าขัดข้อง: $errorMsg")
            }
        }
    }

    /**
     * Add Xtream Codes credentials (which builds M3U stream)
     */
    fun addXtreamPlaylist(name: String, host: String, user: String, pass: String) {
        if (name.isBlank() || host.isBlank() || user.isBlank() || pass.isBlank()) {
            viewModelScope.launch {
                _actionFeedback.emit("กรุณากรอกข้อมูลบัญชีให้ครบถ้วน")
            }
            return
        }

        viewModelScope.launch {
            _playlistLoadingStatus.value = PlaylistLoadStatus.Loading("กำลังเชื่อมต่อเซิร์ฟเวอร์ Xtream API...")
            val result = repository.addPlaylist(
                name = name,
                url = "",
                type = "xtream",
                username = user,
                password = pass,
                host = host
            )
            if (result.isSuccess) {
                val newId = result.getOrNull()
                _selectedPlaylistId.value = newId
                _selectedCategory.value = null
                _playlistLoadingStatus.value = PlaylistLoadStatus.Success("เข้าสู่ระบบ Xtream สำเร็จ!")
                _actionFeedback.emit("เชื่อมต่อสำเร็จ!")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _playlistLoadingStatus.value = PlaylistLoadStatus.Error(errorMsg)
                _actionFeedback.emit("ไม่สามารถล็อกอินหรือซิงก์ข้อมูล: $errorMsg")
            }
        }
    }

    /**
     * Repair Playlist ("ซ่อมแซมไฟล์")
     */
    fun repairCurrentPlaylist() {
        val playlistId = _selectedPlaylistId.value
        if (playlistId == null) {
            viewModelScope.launch {
                _actionFeedback.emit("กรุณาเลือกเพลย์ลิสต์เพื่อซ่อมแซม")
            }
            return
        }

        viewModelScope.launch {
            _playlistLoadingStatus.value = PlaylistLoadStatus.Loading("กำลังซ่อมแซมและกู้คืนไฟล์เพลย์ลิสต์...")
            val result = repository.repairPlaylist(playlistId)
            if (result.isSuccess) {
                _playlistLoadingStatus.value = PlaylistLoadStatus.Success("ตรวจสอบและซ่อมแซมเสร็จสิ้น!")
                _actionFeedback.emit("ระบบกู้คืนไฟล์สำเร็จแล้ว!")
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown Repair Error"
                _playlistLoadingStatus.value = PlaylistLoadStatus.Error(errorMsg)
                _actionFeedback.emit("แก้ไขข้อผิดพลาดล้มเหลว: $errorMsg")
            }
        }
    }

    /**
     * Deletes selected playlist
     */
    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
                _selectedCategory.value = null
            }
            _actionFeedback.emit("ลบเพลย์ลิสต์สำเร็จ")
        }
    }

    /**
     * Resets the loading status so form can be focused or cleared
     */
    fun clearLoadingStatus() {
        _playlistLoadingStatus.value = PlaylistLoadStatus.Idle
    }

    /**
     * Loads working fallback demo streams for system testing
     */
    fun reloadDemoStreams() {
        viewModelScope.launch {
            _playlistLoadingStatus.value = PlaylistLoadStatus.Loading("กำลังโหลดช่องและวีดีโอสตรีมสาธิต...")
            repository.loadFallbackDemoStreams()
            _playlistLoadingStatus.value = PlaylistLoadStatus.Success("สตรีมทดสอบโหลดสำเร็จ!")
            _actionFeedback.emit("สตรีมทดสอบพร้อมใช้งาน")
        }
    }
}

sealed class PlaylistLoadStatus {
    object Idle : PlaylistLoadStatus()
    data class Loading(val message: String) : PlaylistLoadStatus()
    data class Success(val message: String) : PlaylistLoadStatus()
    data class Error(val message: String) : PlaylistLoadStatus()
}

data class IptvFilter(
    val tab: String,
    val playlistId: Int?,
    val category: String?,
    val query: String,
    val showFavs: Boolean
)

