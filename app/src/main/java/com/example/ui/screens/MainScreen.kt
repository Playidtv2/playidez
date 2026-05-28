package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ChannelItem
import com.example.data.model.Playlist
import com.example.data.model.PlaylistError
import com.example.ui.player.VideoPlayer
import com.example.ui.viewmodel.IptvViewModel
import com.example.ui.viewmodel.PlaylistLoadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: IptvViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // ViewModel states
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()
    val playlistLoadingStatus by viewModel.playlistLoadingStatus.collectAsStateWithLifecycle()
    val currentPlayingChannel by viewModel.currentPlayingChannel.collectAsStateWithLifecycle()
    val actionFeedback = viewModel.actionFeedback
    
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val filteredChannels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val currentPlaylistErrors by viewModel.currentPlaylistErrors.collectAsStateWithLifecycle()

    // Dialog sheets states
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var showPlaylistManagerDialog by remember { mutableStateOf(false) }

    // Display feedback toast
    LaunchedEffect(key1 = actionFeedback) {
        actionFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)), // Artistic rounded squircle
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "IPTV Xtreme Player",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    // Quick Demo Loader if playlists are empty
                    if (playlists.isEmpty()) {
                        TextButton(
                            onClick = { viewModel.reloadDemoStreams() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ติดตั้งสตรีมสาธิต")
                        }
                    }

                    // Playlist config button
                    IconButton(
                        onClick = { showPlaylistManagerDialog = true },
                        modifier = Modifier.testTag("manage_playlists_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage Playlists",
                            tint = Color.White
                        )
                    }

                    // Import shortcut
                    Button(
                        onClick = { showAddPlaylistDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("import_playlist_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Import", tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("เพลย์ลิสต์", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Two-pane: Main list layout + Active Player frame
            if (currentPlayingChannel != null) {
                // If the player is active, show the Video Player on top (with proper immersive backing)
                VideoPlayer(
                    channel = currentPlayingChannel!!,
                    onClose = { viewModel.playChannel(null) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Regular Dashboard Home
                Column(modifier = Modifier.fillMaxSize()) {
                    
                    // 1. App Web IPTV Styled Feature Banner
                    WebIptvFeatureBanner(
                        playlistsCount = playlists.size,
                        channelsCount = filteredChannels.size,
                        onImportClick = { showAddPlaylistDialog = true }
                    )

                    // 2. Tab Navigation: ทีวี (Live TV), หนัง (VOD Movies), ซีรีย์ (Series)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TabMenuButton(
                            title = "ทีวีถ่ายทอดสด (LIVE)",
                            iconEmoji = "📺",
                            isSelected = currentTab == "TV",
                            onClick = { viewModel.setTab("TV") },
                            modifier = Modifier.weight(1f)
                        )
                        TabMenuButton(
                            title = "ภาพยนตร์ย้อนหลัง",
                            iconEmoji = "🎬",
                            isSelected = currentTab == "Movie",
                            onClick = { viewModel.setTab("Movie") },
                            modifier = Modifier.weight(1f)
                        )
                        TabMenuButton(
                            title = "ซีรีส์ยอดฮิต",
                            iconEmoji = "🎞",
                            isSelected = currentTab == "Series",
                            onClick = { viewModel.setTab("Series") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 3. Search Bar, Playlist and Favorite Filter row
                    FilterSearchControlRow(
                        query = searchQuery,
                        onQueryChange = { viewModel.setQuery(it) },
                        selectedPlaylistId = selectedPlaylistId,
                        playlists = playlists,
                        onPlaylistSelect = { viewModel.selectPlaylist(it) },
                        showFavoritesOnly = showFavoritesOnly,
                        onFavoritesToggle = { viewModel.toggleFavoritesOnly() }
                    )

                    // 4. Horizontal Categories List (within selected Supercategory)
                    if (categories.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { viewModel.selectCategory(null) },
                                    label = { Text("หมวดหมู่ทั้งหมด") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = Color.White,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                            items(categories) { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { viewModel.selectCategory(cat) },
                                    label = { Text(cat) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = Color.White,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    // 5. Channel grid displays
                    if (filteredChannels.isEmpty()) {
                        EmptyStateDashboard(
                            searchQuery = searchQuery,
                            showFavoritesOnly = showFavoritesOnly,
                            playlistId = selectedPlaylistId,
                            onClearFilters = {
                                viewModel.setQuery("")
                                viewModel.selectCategory(null)
                                if (showFavoritesOnly) viewModel.toggleFavoritesOnly()
                            },
                            onImportMock = { viewModel.reloadDemoStreams() }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(filteredChannels, key = { it.id }) { channel ->
                                ChannelGridItem(
                                    channel = channel,
                                    onClick = { viewModel.playChannel(channel) },
                                    onFavoriteToggle = { viewModel.toggleChannelFavorite(channel) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogs setup
    if (showAddPlaylistDialog) {
        ImportPlaylistDialog(
            status = playlistLoadingStatus,
            onDismiss = {
                showAddPlaylistDialog = false
                viewModel.clearLoadingStatus()
            },
            onAddM3u = { name, url -> viewModel.addM3uPlaylist(name, url) },
            onAddXtream = { name, host, user, pass -> viewModel.addXtreamPlaylist(name, host, user, pass) }
        )
    }

    if (showPlaylistManagerDialog) {
        PlaylistManagerDialog(
            activeId = selectedPlaylistId,
            playlists = playlists,
            errors = currentPlaylistErrors,
            onSelect = { viewModel.selectPlaylist(it) },
            onDelete = { viewModel.deletePlaylist(it) },
            onRepair = { viewModel.repairCurrentPlaylist() },
            onDismiss = { showPlaylistManagerDialog = false },
            status = playlistLoadingStatus,
            clearStatus = { viewModel.clearLoadingStatus() }
        )
    }
}

// Visual layout builders
@Composable
fun WebIptvFeatureBanner(
    playlistsCount: Int,
    channelsCount: Int,
    onImportClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(130.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ambient dark background gradient overlay using theme colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "เว็บแอพสตรีมมิ่งความละเอียดสูง",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ระบบเล่นไฟล์ IPTV เอ็กซ์ตรีม",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("เพลย์ลิสต์: $playlistsCount รายการ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ช่องทีวี & หนัง: $channelsCount รายการ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .clickable { onImportClick() }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("เพลย์ลิสต์ใหม่", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TabMenuButton(
    title: String,
    iconEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = iconEmoji,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = title,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSearchControlRow(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedPlaylistId: Int?,
    playlists: List<Playlist>,
    onPlaylistSelect: (Int?) -> Unit,
    showFavoritesOnly: Boolean,
    onFavoritesToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Search field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("ค้นหารายการช่อง หนัง หรือหมวดหมู่...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_channels_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Lower filter toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Dropdown mock for playlists selection
                var dropExpanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { dropExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val activeName = playlists.find { it.id == selectedPlaylistId }?.name ?: "เพลย์ลิสต์ทั้งหมด"
                            Text(activeName, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Drop", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    DropdownMenu(
                        expanded = dropExpanded,
                        onDismissRequest = { dropExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        DropdownMenuItem(
                            text = { Text("เพลย์ลิสต์ทั้งหมด", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                onPlaylistSelect(null)
                                dropExpanded = false
                            }
                        )
                        playlists.forEach { playlist ->
                            DropdownMenuItem(
                                text = { Text(playlist.name, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    onPlaylistSelect(playlist.id)
                                    dropExpanded = false
                                }
                            )
                        }
                    }
                }

                // Favorites Filter Toggle
                IconButton(
                    onClick = onFavoritesToggle,
                    modifier = Modifier
                        .background(
                            if (showFavoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(16.dp)
                        )
                        .size(38.dp)
                        .testTag("favorite_filter_toggle")
                ) {
                    Icon(
                        imageVector = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Show Favorites",
                        tint = if (showFavoritesOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelGridItem(
    channel: ChannelItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onFavoriteToggle
            )
            .testTag("channel_item_card_${channel.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TV Logo box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.1f)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    } else {
                        // Custom vector icon fallback based on type helper
                        val fallbackIcon = when (channel.superCategory) {
                            "Movie" -> Icons.Default.PlayArrow
                            "Series" -> Icons.Default.List
                            else -> Icons.Default.Home
                        }
                        Icon(
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    // Format indicator tag (HLS/DASH/MP4) on the image
                    val extLabel = when {
                        channel.url.contains(".m3u8") -> "HLS"
                        channel.url.contains(".mpd") -> "DASH"
                        else -> "MP4"
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = extLabel,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Title info details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.9f)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp)
                ) {
                    Text(
                        text = channel.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channel.category,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Small Floating Favorite Action
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite Toggle",
                    tint = if (channel.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Dialog for playlist imports
@Composable
fun ImportPlaylistDialog(
    status: PlaylistLoadStatus,
    onDismiss: () -> Unit,
    onAddM3u: (String, String) -> Unit,
    onAddXtream: (String, String, String, String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .testTag("import_playlist_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "เพิ่มเพลย์ลิสต์ IPTV",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Loading indicators
                when (status) {
                    is PlaylistLoadStatus.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(status.message, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                    is PlaylistLoadStatus.Success -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(status.message, color = Color.Green, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onDismiss) { Text("ปิดหน้าต่าง") }
                        }
                    }
                    is PlaylistLoadStatus.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(status.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                                ) {
                                    Text("ปิดตัวแก้นี้", color = Color.White)
                                }
                            }
                        }
                    }
                    is PlaylistLoadStatus.Idle -> {
                        var selectedImportType by remember { mutableStateOf("m3u") } // "m3u" or "xtream"

                        // Type chooser tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedImportType == "m3u") MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { selectedImportType = "m3u" }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "ลิงก์ M3U URL",
                                    color = if (selectedImportType == "m3u") Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedImportType == "xtream") MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { selectedImportType = "xtream" }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Xtream Codes API",
                                    color = if (selectedImportType == "xtream") Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Form display
                        if (selectedImportType == "m3u") {
                            var name by remember { mutableStateOf("") }
                            var url by remember { mutableStateOf("") }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("ชื่อรายการเพลย์ลิสต์") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("playlist_name_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("M3U URL (เช่น http://...)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("playlist_url_input")
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = { onAddM3u(name, url) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_m3u_button")
                            ) {
                                Text("ดาวน์โหลดและนำเข้าข้อมูล", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Xtream Codes login
                            var name by remember { mutableStateOf("") }
                            var host by remember { mutableStateOf("") }
                            var user by remember { mutableStateOf("") }
                            var pass by remember { mutableStateOf("") }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("ชื่อสมาขิก/ค่ายให้บริการ") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = { Text("Host Server (เช่น http://tv.xtream.com:80)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = user,
                                onValueChange = { user = it },
                                label = { Text("ชื่อผู้ใช้ (Username)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = pass,
                                onValueChange = { pass = it },
                                label = { Text("รหัสผ่าน (Password)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { onAddXtream(name, host, user, pass) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("ล็อกอิน Xtream เครือข่าย", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialog to manage uploaded playlists and perform direct repair ("ซ่อมไฟล์")
@Composable
fun PlaylistManagerDialog(
    activeId: Int?,
    playlists: List<Playlist>,
    errors: List<PlaylistError>,
    onSelect: (Int?) -> Unit,
    onDelete: (Int) -> Unit,
    onRepair: () -> Unit,
    onDismiss: () -> Unit,
    status: PlaylistLoadStatus,
    clearStatus: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "การจัดการเพลย์ลิสต์ & ซ่อมแซมไฟล์",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Repair operational overlay state
                when (status) {
                    is PlaylistLoadStatus.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(status.message, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    is PlaylistLoadStatus.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(status.message, color = Color.Green, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = clearStatus) { Text("กลับสู่หน้าหลัก") }
                            }
                        }
                    }
                    is PlaylistLoadStatus.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(status.message, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = clearStatus) { Text("ตกลง / ลองใหม่") }
                        }
                    }
                    is PlaylistLoadStatus.Idle -> {
                        // Regular flow
                        if (playlists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("ไม่มีไฟล์เพลย์ลิสต์ในโมดูลแอปนิ", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            Text("สลับเพลย์ลิสต์ที่รับชม:", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .maxHeightIn(max = 180.dp)
                            ) {
                                items(playlists) { playlist ->
                                    val isSelected = playlist.id == activeId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.3f
                                                ) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(
                                                    alpha = 0.05f
                                                ),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onSelect(playlist.id) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (playlist.type == "xtream") "ค่าย API: ${playlist.host}" else "ประเภท: .m3u",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = { onDelete(playlist.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active playlist Repair & Diagnostics console
                        if (activeId != null) {
                            val activePlaylist = playlists.find { it.id == activeId }
                            if (activePlaylist != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("เครื่องมือซ่อมและกู้ไฟล์เพลย์ลิสต์", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            TextButton(
                                                onClick = onRepair,
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("ซ่อมแซมไฟล์", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "กรณีช่องไม่เห็นในแอพ หรือดึงข้อมูลขัดข้อง สามารถปุ่มซ่อมแซมเพื่อจัดลำดับและล้างแคชไฟล์ใหม่",
                                            color = Color.Gray,
                                            fontSize = 9.sp
                                        )

                                        if (errors.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("บันทึกวิเคราะห์ปัญหา (${errors.size} จุด):", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            
                                            LazyColumn(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(80.dp)
                                                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            ) {
                                                items(errors) { err ->
                                                    Text(
                                                        text = "[บรรทัดที่ ${err.lineNum}] ${err.errorMessage} (${err.lineContent})",
                                                        color = Color.LightGray,
                                                        fontSize = 9.sp,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("เซ็กเคิลเพลย์ลิสต์ผ่าน ไร้ข้อผิดพลาดไวยากรณ์", color = Color.Green, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialog helper formatting constraints
@Composable
fun EmptyStateDashboard(
    searchQuery: String,
    showFavoritesOnly: Boolean,
    playlistId: Int?,
    onClearFilters: () -> Unit,
    onImportMock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (showFavoritesOnly) Icons.Default.FavoriteBorder else Icons.Default.Search,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (showFavoritesOnly) "ไม่พบรายการที่ชื่นชอบ" else "ไม่พบช่องรายการใดๆ ไนหมวดหมู่นี้",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "ให้ตรวจสอบการพิมพ์ค้นหา หรือลองล้างค่ากรองด้านล่างเพื่อแสดงข้อมูลทั้งหมด",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onClearFilters) {
                Text("ล้างการกรองข้อมูล")
            }

            if (playlistId == null) {
                OutlinedButton(
                    onClick = onImportMock,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("ดึงช่องสตรีมทดสอบสาธิต")
                }
            }
        }
    }
}

// Lazy column max-height helper modifier to limit list heights inside popup dialogue boxes
fun Modifier.maxHeightIn(max: androidx.compose.ui.unit.Dp): Modifier = this.then(
    object : androidx.compose.ui.Modifier.Element {
        // Simple custom layout sizing constraints
    }
)
