package com.example.musicbrainzrenamer

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class Mp3Info(
    val uri: Uri,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String
)

sealed class Screen {
    object Renamer : Screen()
    object LrcEditor : Screen()
    object LrcSync : Screen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = MusicBrainzApi.create()
    private val deezerApi = DeezerApi.create()

    val currentScreen = MutableStateFlow<Screen>(Screen.Renamer)

    val selectedMp3 = MutableStateFlow<Mp3Info?>(null)
    val searchResults = MutableStateFlow<List<Recording>>(emptyList())
    val isSearching = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    val renameStatus = MutableStateFlow<String?>(null)
    val pendingLrcRename = MutableStateFlow<String?>(null)

    val deezerResults = MutableStateFlow<List<DeezerAlbum>>(emptyList())
    val isSearchingDeezer = MutableStateFlow(false)
    val deezerError = MutableStateFlow<String?>(null)
    val coverEmbedStatus = MutableStateFlow<String?>(null)

    val lrcUri = MutableStateFlow<Uri?>(null)
    val lrcText = MutableStateFlow("")
    val lrcEditorStatus = MutableStateFlow<String?>(null)

    val syncLrcUri = MutableStateFlow<Uri?>(null)
    val syncLrcLines = MutableStateFlow<List<LrcLine>>(emptyList())
    val syncOriginalLines = MutableStateFlow<List<LrcLine>>(emptyList())
    val syncOffsetMs = MutableStateFlow(0L)
    val syncStatus = MutableStateFlow<String?>(null)

    fun loadMp3(uri: Uri, context: Context) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val info = Mp3Info(
                uri = uri,
                fileName = getFileName(context, uri) ?: "unknown.mp3",
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "",
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            )
            selectedMp3.value = info
            searchResults.value = emptyList()
            deezerResults.value = emptyList()
            renameStatus.value = null
            pendingLrcRename.value = null
            coverEmbedStatus.value = null
            deezerError.value = null
        } catch (e: Exception) {
            renameStatus.value = "Error reading MP3: ${e.message}"
        } finally {
            retriever.release()
        }
    }

    fun searchMusicBrainz(title: String, artist: String) {
        viewModelScope.launch {
            isSearching.value = true
            searchError.value = null
            try {
                delay(1000)
                val q = buildString {
                    if (artist.isNotBlank()) append("""artist:"${artist.replace("\"", "\\\"")}" """)
                    if (title.isNotBlank()) append("""recording:"${title.replace("\"", "\\\"")}""")
                }
                searchResults.value = api.searchRecording(q).recordings
            } catch (e: Exception) {
                searchError.value = "Search failed: ${e.message}"
            }
            isSearching.value = false
        }
    }

    fun renameMp3(recording: Recording, context: Context) {
        val mp3 = selectedMp3.value ?: return
        viewModelScope.launch {
            try {
                val ext = mp3.fileName.substringAfterLast('.', 'mp3')
                val newName = "${recording.getArtistName()} - ${recording.title}.$ext"
                    .replace(Regex("[\\\\/:*?"<>|]"), "_")
                val doc = DocumentFile.fromSingleUri(context, mp3.uri)
                if (doc?.renameTo(newName) == true) {
                    renameStatus.value = "Renamed to: $newName"
                    selectedMp3.value = mp3.copy(fileName = newName)
                    pendingLrcRename.value = newName.substringBeforeLast('.') + ".lrc"
                } else {
                    renameStatus.value = "Rename failed. Grant 'All Files Access' in settings."
                }
            } catch (e: Exception) {
                renameStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun renameLrc(lrcUri: Uri, context: Context) {
        val newName = pendingLrcRename.value ?: return
        viewModelScope.launch {
            try {
                val doc = DocumentFile.fromSingleUri(context, lrcUri)
                if (doc?.renameTo(newName) == true) {
                    renameStatus.value = (renameStatus.value ?: "") + "\nLRC renamed to: $newName"
                    pendingLrcRename.value = null
                } else {
                    renameStatus.value = (renameStatus.value ?: "") + "\nLRC rename failed."
                }
            } catch (e: Exception) {
                renameStatus.value = (renameStatus.value ?: "") + "\nLRC error: ${e.message}"
            }
        }
    }

    fun searchDeezer(artist: String, album: String) {
        viewModelScope.launch {
            isSearchingDeezer.value = true
            deezerError.value = null
            deezerResults.value = emptyList()
            try {
                val q = buildString {
                    if (artist.isNotBlank()) append("$artist ")
                    if (album.isNotBlank()) append(album)
                }.trim()
                deezerResults.value = deezerApi.searchAlbum(q).data
            } catch (e: Exception) {
                deezerError.value = "Deezer search failed: ${e.message}"
            }
            isSearchingDeezer.value = false
        }
    }

    fun embedCover(album: DeezerAlbum, context: Context) {
        val mp3 = selectedMp3.value ?: return
        viewModelScope.launch {
            coverEmbedStatus.value = "Downloading cover..."
            try {
                val imageUrl = album.coverXl.takeIf { it.isNotBlank() }
                    ?: album.coverBig.takeIf { it.isNotBlank() }
                    ?: album.cover

                val bytes = downloadImage(imageUrl)
                val mime = if (imageUrl.contains(".png", true)) "image/png" else "image/jpeg"

                coverEmbedStatus.value = "Baking into MP3..."
                val result = Mp3TagWriter.embedAlbumArt(mp3.uri, bytes, mime, context)
                coverEmbedStatus.value = result.getOrElse { "Error: ${it.message}" }
            } catch (e: Exception) {
                coverEmbedStatus.value = "Error: ${e.message}"
            }
        }
    }

    private suspend fun downloadImage(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = java.net.URL(url).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
        connection.getInputStream().use { it.readBytes() }
    }

    fun loadLrc(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                } ?: ""
                lrcUri.value = uri
                lrcText.value = content
                lrcEditorStatus.value = "Loaded: ${getFileName(context, uri)}"
            } catch (e: Exception) {
                lrcEditorStatus.value = "Error loading LRC: ${e.message}"
            }
        }
    }

    fun saveLrc(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(lrcText.value) }
                }
                lrcEditorStatus.value = "Saved successfully!"
            } catch (e: Exception) {
                lrcEditorStatus.value = "Error saving: ${e.message}"
            }
        }
    }

    fun createNewLrc() {
        lrcUri.value = null
        lrcText.value = "[ti:Title]\n[ar:Artist]\n[al:Album]\n[00:00.00]First line\n"
        lrcEditorStatus.value = "New LRC created"
    }

    fun loadLrcForSync(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                } ?: ""
                val lines = LrcUtils.parse(content)
                syncLrcUri.value = uri
                syncLrcLines.value = lines
                syncOriginalLines.value = lines
                syncOffsetMs.value = 0L
                syncStatus.value = "Loaded ${lines.size} lines"
            } catch (e: Exception) {
                syncStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun updateSyncOffset(offset: Long) {
        syncOffsetMs.value = offset
        syncLrcLines.value = LrcUtils.applyOffset(syncOriginalLines.value, offset)
    }

    fun saveSyncedLrc(context: Context) {
        val uri = syncLrcUri.value ?: return
        viewModelScope.launch {
            try {
                val text = LrcUtils.toText(syncLrcLines.value)
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(text) }
                }
                syncStatus.value = "Synced LRC saved!"
                syncOriginalLines.value = syncLrcLines.value
                syncOffsetMs.value = 0L
            } catch (e: Exception) {
                syncStatus.value = "Save error: ${e.message}"
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }
}
