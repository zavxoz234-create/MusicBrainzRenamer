package com.example.musicbrainzrenamer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.musicbrainzrenamer.ui.theme.MusicBrainzRenamerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicBrainzRenamerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(vm: MainViewModel = viewModel()) {
    val screen by vm.currentScreen.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("This app needs 'All Files Access' to rename MP3/LRC files.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                permissionLauncher.launch(intent)
            }) {
                Text("Grant Permission")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MusicBrainz Renamer") },
                actions = {
                    TextButton(onClick = { vm.currentScreen.value = Screen.Renamer }) {
                        Text("Renamer", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    TextButton(onClick = { vm.currentScreen.value = Screen.LrcEditor }) {
                        Text("Editor", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    TextButton(onClick = { vm.currentScreen.value = Screen.LrcSync }) {
                        Text("Sync", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (screen) {
                is Screen.Renamer -> RenamerScreen(vm)
                is Screen.LrcEditor -> LrcEditorScreen(vm)
                is Screen.LrcSync -> LrcSyncScreen(vm)
            }
        }
    }
}

@Composable
fun RenamerScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val mp3 by vm.selectedMp3.collectAsState()
    val results by vm.searchResults.collectAsState()
    val searching by vm.isSearching.collectAsState()
    val error by vm.searchError.collectAsState()
    val status by vm.renameStatus.collectAsState()
    val pendingLrc by vm.pendingLrcRename.collectAsState()

    val deezerResults by vm.deezerResults.collectAsState()
    val searchingDeezer by vm.isSearchingDeezer.collectAsState()
    val deezerError by vm.deezerError.collectAsState()
    val coverStatus by vm.coverEmbedStatus.collectAsState()

    val pickMp3 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadMp3(it, context) }
    }

    val pickLrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.renameLrc(it, context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Button(
            onClick = { pickMp3.launch(arrayOf("audio/mpeg", "audio/mp3")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select MP3 File")
        }

        mp3?.let { info ->
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current: ${info.fileName}", style = MaterialTheme.typography.titleSmall)
                    Text("Title: ${info.title}")
                    Text("Artist: ${info.artist}")
                    Text("Album: ${info.album}")
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { vm.searchMusicBrainz(info.title, info.artist) },
                    modifier = Modifier.weight(1f),
                    enabled = !searching
                ) {
                    Text(if (searching) "..." else "MusicBrainz")
                }
                Button(
                    onClick = { vm.searchDeezer(info.artist, info.album) },
                    modifier = Modifier.weight(1f),
                    enabled = !searchingDeezer
                ) {
                    Text(if (searchingDeezer) "..." else "Find Cover")
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (results.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("MusicBrainz Results:", style = MaterialTheme.typography.titleMedium)
                results.forEach { rec ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { vm.renameMp3(rec, context) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(rec.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${rec.getArtistName()} — ${rec.getAlbumName()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            deezerError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (deezerResults.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Album Covers (Deezer):", style = MaterialTheme.typography.titleMedium)
                deezerResults.forEach { album ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = album.coverBig,
                                contentDescription = "Cover",
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(album.title, style = MaterialTheme.typography.bodyLarge)
                                Text(album.artist.name, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { vm.embedCover(album, context) }) {
                                Text("Embed")
                            }
                        }
                    }
                }
            }

            coverStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            pendingLrc?.let {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "MP3 renamed! Now select the matching LRC file to rename it too.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { pickLrc.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select LRC to Rename")
                        }
                    }
                }
            }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun LrcEditorScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val text by vm.lrcText.collectAsState()
    val status by vm.lrcEditorStatus.collectAsState()
    val uri by vm.lrcUri.collectAsState()

    val pickLrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { vm.loadLrc(it, context) }
    }

    val createLrc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { u ->
        u?.let { vm.saveLrc(it, context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { pickLrc.launch(arrayOf("text/plain", "*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Open LRC")
            }
            Button(
                onClick = { vm.createNewLrc() },
                modifier = Modifier.weight(1f)
            ) {
                Text("New")
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { vm.lrcText.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("LRC Content") },
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (uri != null) vm.saveLrc(uri!!, context)
                    else createLrc.launch("lyrics.lrc")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
            Button(
                onClick = { createLrc.launch("lyrics.lrc") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save As")
            }
        }

        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun LrcSyncScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val lines by vm.syncLrcLines.collectAsState()
    val offset by vm.syncOffsetMs.collectAsState()
    val status by vm.syncStatus.collectAsState()
    val original by vm.syncOriginalLines.collectAsState()

    val pickLrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { vm.loadLrcForSync(it, context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = { pickLrc.launch(arrayOf("text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Load LRC for Sync")
        }

        Spacer(Modifier.height(12.dp))

        if (original.isNotEmpty()) {
            Text("Offset: ${offset}ms", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = offset.toFloat(),
                onValueChange = { vm.updateSyncOffset(it.toLong()) },
                valueRange = -10000f..10000f,
                steps = 200
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { vm.updateSyncOffset(offset - 100) }) { Text("-100ms") }
                Button(onClick = { vm.updateSyncOffset(offset - 500) }) { Text("-500ms") }
                Button(onClick = { vm.updateSyncOffset(offset + 500) }) { Text("+500ms") }
                Button(onClick = { vm.updateSyncOffset(offset + 100) }) { Text("+100ms") }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.saveSyncedLrc(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Synced LRC")
            }

            Spacer(Modifier.height(12.dp))
            Text("Preview:", style = MaterialTheme.typography.titleSmall)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(lines) { line ->
                    Text(
                        LrcUtils.formatLine(line),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}
