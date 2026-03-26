package com.usbdiskmanager.ps2.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usbdiskmanager.ps2.data.converter.UlCfgManager
import com.usbdiskmanager.ps2.data.scanner.IsoScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class SourceCfg(
    val fileName: String,
    val file: File,
    val entries: List<UlCfgManager.UlEntry>
)

@Composable
fun UlCfgMergerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfgManager = remember { UlCfgManager() }

    var sources by remember { mutableStateOf<List<SourceCfg>>(emptyList()) }
    var destEntries by remember { mutableStateOf<List<UlCfgManager.UlEntry>>(emptyList()) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Editing: maps (sourceIndex -> gameIndex) -> edited name
    var editedNames by remember { mutableStateOf<Map<Pair<Int, Int>, String>>(emptyMap()) }
    var editingKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editDialogText by remember { mutableStateOf("") }

    val defaultCfgFile = remember { File(IsoScanner.DEFAULT_UL_DIR, "ul.cfg") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (defaultCfgFile.exists()) {
                destEntries = cfgManager.readAllEntries(defaultCfgFile)
            }
        }
    }

    fun mergeAll() {
        if (sources.isEmpty()) {
            resultMessage = "Ajoutez au moins un fichier source."
            return
        }
        scope.launch {
            isProcessing = true
            withContext(Dispatchers.IO) {
                try {
                    defaultCfgFile.parentFile?.mkdirs()
                    var totalAdded = 0
                    sources.forEachIndexed { si, source ->
                        // Apply any edited names to source entries
                        val entries = source.entries.mapIndexed { gi, entry ->
                            val newName = editedNames[si to gi]
                            if (newName != null && newName.isNotBlank()) entry.copy(gameName = newName.take(32))
                            else entry
                        }
                        val tempFile = File(context.cacheDir, "ul_merge_temp_$si.cfg")
                        cfgManager.writeEntriesPublic(tempFile, entries)
                        totalAdded += cfgManager.mergeInto(tempFile, defaultCfgFile)
                        tempFile.delete()
                    }
                    destEntries = cfgManager.readAllEntries(defaultCfgFile)
                    resultMessage = "✓ Fusion terminée — $totalAdded nouveau(x) jeu(x) ajouté(s). Total: ${destEntries.size}."
                } catch (e: Exception) {
                    resultMessage = "Erreur fusion: ${e.message}"
                }
            }
            isProcessing = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                withContext(Dispatchers.IO) {
                    try {
                        val idx = sources.size
                        val temp = File(context.cacheDir, "ul_src_$idx.cfg")
                        context.contentResolver.openInputStream(uri)?.use { inp ->
                            temp.outputStream().use { out -> inp.copyTo(out) }
                        }
                        val entries = cfgManager.readAllEntries(temp)
                        if (entries.isEmpty()) {
                            resultMessage = "Fichier vide ou format invalide."
                        } else {
                            val name = uri.lastPathSegment ?: "source_$idx.cfg"
                            sources = sources + SourceCfg(name, temp, entries)
                            resultMessage = null
                        }
                    } catch (e: Exception) {
                        resultMessage = "Erreur lecture: ${e.message}"
                    }
                }
                isProcessing = false
            }
        }
    }

    // Compute all source games with dedup info
    val allSourceGames: List<Triple<Int, Int, UlCfgManager.UlEntry>> = sources
        .flatMapIndexed { si, src ->
            src.entries.mapIndexed { gi, e -> Triple(si, gi, e) }
        }
    val destIds = destEntries.map { it.gameIdClean }.toSet()
    val sourceIds = mutableSetOf<String>()
    val isDuplicate: (UlCfgManager.UlEntry) -> Boolean = { entry ->
        entry.gameIdClean in destIds || !sourceIds.add(entry.gameIdClean)
    }

    // Edit dialog
    editingKey?.let { key ->
        AlertDialog(
            onDismissRequest = { editingKey = null },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Renommer le jeu") },
            text = {
                OutlinedTextField(
                    value = editDialogText,
                    onValueChange = { editDialogText = it },
                    label = { Text("Nom du jeu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    editedNames = editedNames + (key to editDialogText)
                    editingKey = null
                }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { editingKey = null }) { Text("Annuler") }
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {

        // ── Info banner ──
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.MergeType, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Column {
                        Text("Fusionner plusieurs ul.cfg",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Ajoutez un ou plusieurs fichiers source. Modifiez les noms si besoin. Fusionnez directement ou après révision.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // ── Add source button ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Chargement…")
                    } else {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (sources.isEmpty()) "Ajouter un fichier source" else "Ajouter un autre fichier")
                    }
                }
                if (sources.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            sources = emptyList()
                            editedNames = emptyMap()
                            resultMessage = null
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tout effacer")
                    }
                }
            }
        }

        // ── Source files ──
        sources.forEachIndexed { si, source ->
            item(key = "source_header_$si") {
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Source ${si + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold)
                                Text(source.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${source.entries.size} jeu(x)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                sources = sources.toMutableList().also { it.removeAt(si) }
                                editedNames = editedNames.filterKeys { (k, _) -> k != si }
                                    .mapKeys { (k, v) -> if (k.first > si) (k.first - 1 to k.second) else k }
                            }) {
                                Icon(Icons.Default.Close, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── All source games combined ──
        if (allSourceGames.isNotEmpty()) {
            item(key = "games_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Jeux à fusionner (${allSourceGames.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Appui long = renommer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            itemsIndexed(allSourceGames, key = { _, t -> "game_${t.first}_${t.second}" }) { _, triple ->
                val (si, gi, entry) = triple
                val displayName = editedNames[si to gi] ?: entry.gameName.trimEnd('\u0000').ifBlank { entry.gameIdClean }
                val dup = isDuplicate(entry)

                Surface(
                    color = when {
                        dup -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(entry.gameIdClean,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• S${si + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline)
                                if (dup) {
                                    Text("• doublon",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                editDialogText = displayName
                                editingKey = si to gi
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, null,
                                modifier = Modifier.size(16.dp),
                                tint = if (editedNames.containsKey(si to gi))
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        // ── Destination summary ──
        item(key = "dest_card") {
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Storage, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Destination (ul.cfg principal)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text(
                            if (destEntries.isNotEmpty()) "${destEntries.size} jeu(x) présent(s)"
                            else "Vide ou inexistant",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (destEntries.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                destEntries = if (defaultCfgFile.exists())
                                    cfgManager.readAllEntries(defaultCfgFile) else emptyList()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, null)
                    }
                }
            }
        }

        // ── Result message ──
        resultMessage?.let { msg ->
            item(key = "result") {
                Surface(
                    color = if (msg.startsWith("✓")) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (msg.startsWith("✓")) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (msg.startsWith("✓")) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("✓")) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── Action buttons ──
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Direct merge (no editing required)
                Button(
                    onClick = { mergeAll() },
                    enabled = sources.isNotEmpty() && !isProcessing,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Fusion en cours…")
                    } else {
                        Icon(Icons.Default.MergeType, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fusionner (${allSourceGames.size} jeux)", fontWeight = FontWeight.Bold)
                    }
                }

                val newCount = allSourceGames
                    .filter { (si, gi, e) -> e.gameIdClean !in destIds }
                    .distinctBy { (_, _, e) -> e.gameIdClean }.size
                if (newCount > 0) {
                    Text(
                        "$newCount nouveau(x) jeu(x) sera(ont) ajouté(s) — ${allSourceGames.size - newCount} doublon(s) ignoré(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
