package com.example.data.repository

import android.util.Log
import com.example.data.database.ChannelDao
import com.example.data.database.PlaylistDao
import com.example.data.database.PlaylistErrorDao
import com.example.data.model.ChannelItem
import com.example.data.model.Playlist
import com.example.data.model.PlaylistError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

class IptvRepository(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val playlistErrorDao: PlaylistErrorDao
) {
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    val allChannels: Flow<List<ChannelItem>> = channelDao.getAllChannels()
    val favoriteChannels: Flow<List<ChannelItem>> = channelDao.getFavoriteChannels()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getChannelsByPlaylist(playlistId: Int): Flow<List<ChannelItem>> {
        return channelDao.getChannelsByPlaylist(playlistId)
    }

    fun getChannelsBySuperCategory(superCategory: String): Flow<List<ChannelItem>> {
        return channelDao.getChannelsBySuperCategory(superCategory)
    }

    fun getCategoriesBySuperCategory(superCategory: String): Flow<List<String>> {
        return channelDao.getCategoriesBySuperCategory(superCategory)
    }

    suspend fun updateFavorite(channelId: Int, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            channelDao.updateFavoriteStatus(channelId, isFavorite)
        }
    }

    suspend fun deletePlaylist(playlistId: Int) {
        withContext(Dispatchers.IO) {
            playlistDao.deletePlaylist(playlistId)
            channelDao.deleteChannelsByPlaylist(playlistId)
            playlistErrorDao.deleteErrorsByPlaylist(playlistId)
        }
    }

    fun getErrorsByPlaylist(playlistId: Int): Flow<List<PlaylistError>> {
        return playlistErrorDao.getErrorsByPlaylist(playlistId)
    }

    fun getFilteredChannels(
        tab: String,
        playlistId: Int?,
        category: String?,
        showFavs: Boolean,
        query: String
    ): Flow<List<ChannelItem>> {
        return channelDao.getFilteredChannels(
            tab = tab,
            playlistId = playlistId,
            category = category,
            showFavs = if (showFavs) 1 else 0,
            query = query
        )
    }

    fun getCategoriesByFilter(tab: String, playlistId: Int?): Flow<List<String>> {
        return channelDao.getCategoriesByFilter(tab, playlistId)
    }

    /**
     * Reconstruct M3U URL for Xtream codes logins.
     */
    fun buildXtreamM3uUrl(host: String, user: String, pass: String): String {
        val cleanHost = if (host.endsWith("/")) host.substring(0, host.length - 1) else host
        return "$cleanHost/get.php?username=$user&password=$pass&output=m3u8"
    }

    /**
     * Adds an M3U playlist by parsing either a remote URL or plain text content.
     */
    suspend fun addPlaylist(
        name: String,
        url: String,
        type: String,
        username: String? = null,
        password: String? = null,
        host: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val playlistUrl = if (type == "xtream" && host != null && username != null && password != null) {
                buildXtreamM3uUrl(host, username, password)
            } else {
                url
            }

            val playlist = Playlist(
                name = name,
                url = playlistUrl,
                type = type,
                username = username,
                password = password,
                host = host
            )

            val playlistId = playlistDao.insertPlaylist(playlist).toInt()
            
            // Try downloading and parsing
            val fetchResult = fetchPlaylistContent(playlistUrl)
            if (fetchResult.isSuccess) {
                val content = fetchResult.getOrThrow()
                val (channels, errors) = parseM3uContent(content, playlistId)
                
                // Save database
                channelDao.deleteChannelsByPlaylist(playlistId)
                playlistErrorDao.deleteErrorsByPlaylist(playlistId)
                
                if (channels.isNotEmpty()) {
                    channelDao.insertChannels(channels)
                }
                if (errors.isNotEmpty()) {
                    playlistErrorDao.insertErrors(errors)
                }
                
                Result.success(playlistId)
            } else {
                // If it fails, save playlist entry but with network errors to repair later
                val errorMsg = fetchResult.exceptionOrNull()?.message ?: "Unknown Network Error"
                val errors = listOf(
                    PlaylistError(
                        playlistId = playlistId,
                        lineNum = 0,
                        lineContent = "URL: $playlistUrl",
                        errorMessage = "ไม่สามารถเชื่อมต่อเซิร์ฟเวอร์ได้: $errorMsg"
                    )
                )
                playlistErrorDao.insertErrors(errors)
                Result.failure(Exception("ดาวน์โหลดเพลย์ลิสต์ไม่สำเร็จ: $errorMsg แต่เพิ่มรายการเพื่อซ่อมแซมภายหลังได้"))
            }
        } catch (e: Exception) {
            Log.e("IptvRepository", "Error adding playlist", e)
            Result.failure(e)
        }
    }

    /**
     * Repairs and re-synced a playlist, handling common M3U parsing issues, SSL troubles, etc.
     */
    suspend fun repairPlaylist(playlistId: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val playlist = playlistDao.getPlaylistById(playlistId)
                ?: return@withContext Result.failure(Exception("ไม่พบเพลย์ลิสต์"))

            val playlistUrl = if (playlist.type == "xtream" && playlist.host != null && playlist.username != null && playlist.password != null) {
                buildXtreamM3uUrl(playlist.host, playlist.username, playlist.password)
            } else {
                playlist.url
            }

            // Retry download with a more lenient client/alternative configuration
            val client = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(playlistUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) IPTVStreamPlayer/1.1") // More IPTV servers accept standard agents
                .build()

            val responseResult = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP Code: ${response.code}")
                    response.body?.string() ?: throw Exception(" empty body")
                }
            }

            if (responseResult.isSuccess) {
                val content = responseResult.getOrThrow()
                val (channels, errors) = parseM3uContent(content, playlistId)

                channelDao.deleteChannelsByPlaylist(playlistId)
                playlistErrorDao.deleteErrorsByPlaylist(playlistId)

                if (channels.isNotEmpty()) {
                    channelDao.insertChannels(channels)
                }
                if (errors.isNotEmpty()) {
                    playlistErrorDao.insertErrors(errors)
                }

                playlistDao.insertPlaylist(playlist.copy(lastUpdated = System.currentTimeMillis()))
                Result.success(playlistId)
            } else {
                val errorMsg = responseResult.exceptionOrNull()?.message ?: "Unknown Error"
                val repairErrors = listOf(
                    PlaylistError(
                        playlistId = playlistId,
                        lineNum = 0,
                        lineContent = "Repair Attempt",
                        errorMessage = "ความพยายามในการซ่อมแซมล้มเหลว: $errorMsg"
                    )
                )
                playlistErrorDao.deleteErrorsByPlaylist(playlistId)
                playlistErrorDao.insertErrors(repairErrors)
                Result.failure(Exception("ความพยายามซ่อมแซมล้มเหลว: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchPlaylistContent(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                // Set default player user agents as IPTV links often filter against Java/OkHttp agents
                .header("User-Agent", "IPTV_Player_Android_Media3")
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses M3U contents and splits channels into TV, Movie (หนัง), and Series (ซีรีย์).
     * Collects syntax errors to support the "ซ่อมไฟล์" feature.
     */
    private fun parseM3uContent(content: String, playlistId: Int): Pair<List<ChannelItem>, List<PlaylistError>> {
        val channels = mutableListOf<ChannelItem>()
        val errors = mutableListOf<PlaylistError>()
        
        val reader = BufferedReader(StringReader(content))
        var line: String?
        var lineNum = 0
        
        var extInfFound = false
        var currentMetadata = HashMap<String, String>()
        var currentChannelName = ""
        
        while (reader.readLine().also { line = it } != null) {
            lineNum++
            val trimmedLine = line!!.trim()
            if (trimmedLine.isEmpty()) continue
            
            if (trimmedLine.startsWith("#EXTM3U")) {
                continue
            }
            
            if (trimmedLine.startsWith("#EXTINF:")) {
                extInfFound = true
                currentMetadata.clear()
                currentChannelName = ""
                
                // Parse #EXTINF: -1 tvg-id="..." tvg-logo="..." group-title="...",Channel Name
                try {
                    val commaIndex = trimmedLine.lastIndexOf(',')
                    if (commaIndex != -1) {
                        currentChannelName = trimmedLine.substring(commaIndex + 1).trim()
                        val rawMeta = trimmedLine.substring(0, commaIndex)
                        
                        // Use regex to locate key="value" pairs
                        val regex = "([a-zA-Z0-9_-]+)=\"([^\"]*)\"".toRegex()
                        val matches = regex.findAll(rawMeta)
                        for (match in matches) {
                            val key = match.groupValues[1]
                            val value = match.groupValues[2]
                            currentMetadata[key] = value
                        }
                    } else {
                        currentChannelName = trimmedLine.replace("#EXTINF:", "").trim()
                    }
                } catch (e: Exception) {
                    errors.add(
                        PlaylistError(
                            playlistId = playlistId,
                            lineNum = lineNum,
                            lineContent = trimmedLine,
                            errorMessage = "ไวยากรณ์ #EXTINF ผิดพลาด: ${e.message}"
                        )
                    )
                }
            } else if (trimmedLine.startsWith("#")) {
                // Secondary metadata tag (e.g. #EXTGRP, #EXTVLCOPT)
                continue
            } else {
                // This is a candidate URL line
                if (extInfFound) {
                    val url = trimmedLine
                    val logoUrl = currentMetadata["tvg-logo"]
                    val category = currentMetadata["group-title"] ?: "ช่องทั่วไป"
                    
                    val name = if (currentChannelName.isEmpty()) "ช่องไม่มีชื่อ ($lineNum)" else currentChannelName
                    
                    // Categorize: TV, Movies, Series
                    val superCategory = determineSuperCategory(name, category, url)
                    
                    channels.add(
                        ChannelItem(
                            playlistId = playlistId,
                            name = name,
                            url = url,
                            logoUrl = logoUrl,
                            category = category,
                            superCategory = superCategory
                        )
                    )
                    extInfFound = false
                } else {
                    // Orphaned URL line without #EXTINF
                    errors.add(
                        PlaylistError(
                            playlistId = playlistId,
                            lineNum = lineNum,
                            lineContent = trimmedLine,
                            errorMessage = "พบสตรีม URL แต่ไม่มีข้อมูลกำกับช่อง (#EXTINF) ด้านบน"
                        )
                    )
                }
            }
        }
        
        return Pair(channels, errors)
    }

    /**
     * Standard IPTV categorization algorithm. Matches words in name, category, or URL path.
     */
    private fun determineSuperCategory(name: String, category: String, url: String): String {
        val nameLower = name.lowercase()
        val catLower = category.lowercase()
        val urlLower = url.lowercase()

        // Movie Check (หนัง): Contains "movie", "cinema", "vod", "หนัง", "film", "4k movie", "action", "comedy"
        val movieKeywords = listOf("movie", "cinema", "vod", "หนัง", "film", "action", "comedy", "thriller", "drama", "romance", "horror")
        val isMovie = movieKeywords.any { nameLower.contains(it) || catLower.contains(it) } || urlLower.contains("/movie/") || urlLower.contains("/movies/")

        // Series Check (ซีรีย์): Contains "series", "ซีรีย์", "ซีรี่ส์", "season", "episode", "s01", "s02", "ep01"
        val seriesKeywords = listOf("series", "ซีรีย์", "ซีรี่ส์", "season", "episode", "s0", "ep", "s01", "s02", "s03", "s04", "s05", "ep01", "ep02")
        val isSeries = seriesKeywords.any { nameLower.contains(it) || catLower.contains(it) } || urlLower.contains("/series/") || urlLower.contains("/episodes/")

        return when {
            isSeries -> "Series"
            isMovie -> "Movie"
            else -> "TV"
        }
    }

    /**
     * Injects standard functioning demo video streams to ensure they can test playing HLS, DASH, and MP4.
     */
    suspend fun loadFallbackDemoStreams() = withContext(Dispatchers.IO) {
        val demoPlaylistName = "สตรีมทดสอบระบบ (Demo Playlists)"
        val demoExist = playlistDao.getAllPlaylists().first().firstOrNull { it.name == demoPlaylistName }
        
        val playlistId = if (demoExist != null) {
            demoExist.id
        } else {
            val playlist = Playlist(
                name = demoPlaylistName,
                url = "internal://demo-streams",
                type = "m3u"
            )
            playlistDao.insertPlaylist(playlist).toInt()
        }

        channelDao.deleteChannelsByPlaylist(playlistId)
        playlistErrorDao.deleteErrorsByPlaylist(playlistId)

        val demoChannels = listOf(
            // TV (HLS and DASH streams)
            ChannelItem(
                playlistId = playlistId,
                name = "Akamai HLS Stream (TV)",
                url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                logoUrl = "https://static.opensr.com/p/play.png",
                category = "ช่องทีวีทดสอบ (Live TV Test)",
                superCategory = "TV"
            ),
            ChannelItem(
                playlistId = playlistId,
                name = "Live DASH Clock Player (TV)",
                url = "https://livesim.dashif.org/livesim/testpic_2s/Manifest.mpd",
                logoUrl = "https://static.opensr.com/p/play.png",
                category = "ช่องทีวีทดสอบ (Live TV Test)",
                superCategory = "TV"
            ),
            ChannelItem(
                playlistId = playlistId,
                name = "Longtail Adaptive HLS Stream",
                url = "https://playertest.longtailvideo.com/adaptive/all/playlist.m3u8",
                logoUrl = "https://static.opensr.com/p/play.png",
                category = "ช่องทีวีทดสอบ (Live TV Test)",
                superCategory = "TV"
            ),
            
            // Movies (DASH & MP4 format)
            ChannelItem(
                playlistId = playlistId,
                name = "Sintel Cartoon 1080p (HDR DASH Movie)",
                url = "https://dash.akamaized.net/akamai/test/isobmff-hdr/Sintel_HDR_1080p_20mbps.mpd",
                logoUrl = "https://static.opensr.com/p/movie.png",
                category = "ภาพยนตร์ย้อนหลัง (VOD Movies)",
                superCategory = "Movie"
            ),
            ChannelItem(
                playlistId = playlistId,
                name = "Big Buck Bunny Classic (MP4 Movie)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                logoUrl = "https://static.opensr.com/p/movie.png",
                category = "ภาพยนตร์ย้อนหลัง (VOD Movies)",
                superCategory = "Movie"
            ),

            // Series (MP4 & HLS format)
            ChannelItem(
                playlistId = playlistId,
                name = "Elephants Dream (Series Ep 1)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                logoUrl = "https://static.opensr.com/p/series.png",
                category = "ซีรีย์ฝรั่ง (Drama Series)",
                superCategory = "Series"
            ),
            ChannelItem(
                playlistId = playlistId,
                name = "Tears of Steel HLS (Series Ep 2)",
                url = "https://playertest.longtailvideo.com/adaptive/tears_of_steel/tears_of_steel.m3u8",
                logoUrl = "https://static.opensr.com/p/series.png",
                category = "ซีรีย์ฝรั่ง (Drama Series)",
                superCategory = "Series"
            )
        )

        channelDao.insertChannels(demoChannels)
    }
}
