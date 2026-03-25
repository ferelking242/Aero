package com.usbdiskmanager.ps2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usbdiskmanager.ps2.domain.model.UsbGame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UlManagerScreen(viewModel: Ps2ViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var gameToRename by remember { mutableStateOf<UsbGame?>(null) }
    var gameToDelete by remember { mutableStateOf<UsbGame?>(null) }
    var renameInput by remember { mutableStateOf("") }

    // Rename dialog
    gameToRename?.let { game ->
        AlertDialog(
            onDismissRequest = { gameToRename = null },
            icon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
            title = { Text("Renommer le jeu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Nom actuel : ${game.gameName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it.take(32) },
                        label = { Text("Nouveau nom (max 32 chars)") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Label, null) }
                    )
                    Text(
                        "${renameInput.length}/32",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameUlGame(game, renameInput.trim())
                        }
                        gameToRename = null
                    },
                    enabled = renameInput.isNotBlank() && renameInput.trim() != game.gameName
                ) { Text("Renommer") }
            },
            dismissButton = {
                OutlinedButton(onClick = { gameToRename = null }) { Text("Annuler") }
            }
        )
    }

    // Delete confirmation dialog
    gameToDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            icon = {
                Icon(
                    Icons.Default.DeleteForever, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Supprimer le jeu ?", color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Êtes-vous sûr de vouloir supprimer :")
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(game.gameName, fontWeight = FontWeight.Bold)
                            Text(game.gameId, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text("${game.numParts} partie(s) • ${formatBytes(game.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "Cette action supprimera l'entrée ul.cfg ET tous les fichiers de parts (ul.*.*) associés. Elle est irréversible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteUlGame(game); gameToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Supprimer")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { gameToDelete = null }) { Text("Annuler") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Mount point selector ──
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ManageSearch, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        "UL Manager — Gérer les jeux USBExtreme/OPL",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.availableUsbMounts.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                            Text(
                                "Aucun périphérique USB détecté. Branchez une clé USB.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Text(
                        "Sélectionner la clé USB :",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.availableUsbMounts.forEach { mount ->
                            val isSelected = mount.mountPoint == uiState.ulManagerMount
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectUlManagerMount(mount.mountPoint) },
                                label = {
                                    Text(
                                        java.io.File(mount.mountPoint).name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Usb, null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Error / loading state ──
        uiState.ulManagerError?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Column(Modifier.weight(1f)) {
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    IconButton(onClick = { viewModel.clearUlManagerError() }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            }
        }

        if (uiState.ulManagerLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Lecture du ul.cfg…", style = MaterialTheme.typography.bodySmall)
                }
            }
            return@Column
        }

        if (uiState.ulManagerMount == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Usb, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Sélectionnez une clé USB ci-dessus\npour gérer ses jeux USBExtreme",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Column
        }

        if (uiState.ulGames.isEmpty() && uiState.ulManagerError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.VideogameAssetOff, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Aucun jeu USBExtreme trouvé\nsur cette clé USB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Les jeux doivent être au format USBExtreme\n(fichiers ul.*.* + ul.cfg à la racine)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Column
        }

        // ── Game list header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.VideogameAsset, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    "${uiState.ulGames.size} jeu(x) USBExtreme",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = {
                uiState.ulManagerMount?.let { viewModel.loadUlGames(it) }
            }) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Game list ──
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.ulGames, key = { it.gameId }) { game ->
                UlGameCard(
                    game = game,
                    onRename = {
                        renameInput = game.gameName
                        gameToRename = game
                    },
                    onDelete = { gameToDelete = game }
                )
            }
        }
    }
}

@Composable
private fun UlGameCard(
    game: UsbGame,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Game icon
            Surface(
                color = if (game.isCd) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (game.isCd) Icons.Default.Album else Icons.Default.VideogameAsset,
                        null,
                        tint = if (game.isCd) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    game.gameName.ifBlank { "(Sans nom)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    game.gameId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoBadge(if (game.isCd) "CD" else "DVD", if (game.isCd) Color(0xFF9C27B0) else Color(0xFF1565C0))
                    InfoBadge("${game.numParts} part(s)", MaterialTheme.colorScheme.onSurfaceVariant)
                    if (game.sizeBytes > 0) {
                        InfoBadge(formatBytes(game.sizeBytes), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = onRename,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Edit, "Renommer", modifier = Modifier.size(18.dp))
                }
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Delete, "Supprimer",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Part files list (collapsed by default)
        if (game.partFiles.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    "Fichiers : ${game.partFiles.take(3).joinToString(", ")}${if (game.partFiles.size > 3) " …+${game.partFiles.size - 3}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun InfoBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "?"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> "%.1f Go".format(gb)
        mb >= 1.0 -> "%.0f Mo".format(mb)
        else -> "${bytes / 1024} Ko"
    }
}
