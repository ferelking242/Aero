package com.usbdiskmanager.ps2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usbdiskmanager.ps2.data.transfer.TransferProgress
import com.usbdiskmanager.ps2.domain.model.UsbGame
import com.usbdiskmanager.ps2.util.MountInfo

@Composable
fun UsbTransferScreen(viewModel: Ps2ViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mounts = uiState.availableUsbMounts
    val sourceMount = uiState.transferSourceMount
    val selectedSource = mounts.find { it.mountPoint == sourceMount } ?: mounts.firstOrNull()
    val sourceGames = uiState.transferState.usbGames[selectedSource?.mountPoint] ?: emptyList()
    val selectedIds = uiState.transferSelectedIds
    val otherMounts = mounts.filter { it.mountPoint != selectedSource?.mountPoint }

    var showDestDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSource?.mountPoint) {
        if (selectedSource != null && sourceMount != selectedSource.mountPoint) {
            viewModel.setTransferSourceMount(selectedSource.mountPoint)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showDestDialog = true },
                    icon = { Icon(Icons.Default.Send, null) },
                    text = { Text("Transférer ${selectedIds.size} jeu(x)") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Header ──
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Usb, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Transférer les jeux",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            if (mounts.isEmpty()) "Aucune USB détectée"
                            else "${mounts.size} USB • Sélectionnez les jeux à transférer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.refreshUsbMounts()
                            viewModel.refreshTransferGames()
                        }
                    ) {
                        if (uiState.transferState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, "Rafraîchir",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            if (mounts.isEmpty() && !uiState.transferState.isLoading) {
                NoUsbTransferPlaceholder(modifier = Modifier.weight(1f))
                return@Column
            }

            // ── Source USB selector (if multiple) ──
            if (mounts.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = mounts.indexOfFirst { it.mountPoint == selectedSource?.mountPoint }.coerceAtLeast(0),
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    mounts.forEach { mount ->
                        Tab(
                            selected = mount.mountPoint == selectedSource?.mountPoint,
                            onClick = { viewModel.setTransferSourceMount(mount.mountPoint) },
                            text = {
                                Text(
                                    mount.mountPoint.substringAfterLast('/'),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            icon = { Icon(Icons.Default.Usb, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Select All bar ──
            AnimatedVisibility(visible = sourceGames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectedIds.isEmpty()) "${sourceGames.size} jeux disponibles"
                        else "${selectedIds.size}/${sourceGames.size} sélectionné(s)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedIds.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = viewModel::selectAllTransferGames,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("Tout", style = MaterialTheme.typography.labelMedium) }
                        TextButton(
                            onClick = viewModel::clearTransferSelection,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = selectedIds.isNotEmpty()
                        ) { Text("Aucun", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }

            // ── Game List ──
            if (sourceGames.isEmpty() && !uiState.transferState.isLoading) {
                Column(
                    modifier = Modifier.weight(1f).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.VideogameAssetOff, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(12.dp))
                    Text("Aucun jeu UL trouvé sur cette clé",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("Format attendu: ul.cfg + fichiers ul.*",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Active transfers
                    val activeTransfers = uiState.transferState.activeTransfers
                    if (activeTransfers.isNotEmpty()) {
                        item {
                            Text("En cours",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                        }
                        items(activeTransfers.entries.toList(), key = { "active_${it.key}" }) { (gameId, prog) ->
                            ActiveTransferRow(gameId = gameId, progress = prog)
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                    }

                    items(sourceGames, key = { it.gameId }) { game ->
                        val isSelected = game.gameId in selectedIds
                        val activeProgress = uiState.transferState.activeTransfers[game.gameId]
                        TransferGameRow(
                            game = game,
                            isSelected = isSelected,
                            activeProgress = activeProgress,
                            onToggle = { viewModel.toggleTransferSelection(game) }
                        )
                    }
                }
            }
        }
    }

    // ── Destination Dialog ──
    if (showDestDialog) {
        TransferDestinationDialog(
            selectedCount = selectedIds.size,
            otherMounts = otherMounts,
            onDismiss = { showDestDialog = false },
            onToInternal = {
                showDestDialog = false
                viewModel.batchTransferToInternal()
            },
            onToMount = { mount ->
                showDestDialog = false
                viewModel.batchTransferToMount(mount.mountPoint)
            }
        )
    }
}

@Composable
private fun TransferGameRow(
    game: UsbGame,
    isSelected: Boolean,
    activeProgress: TransferProgress?,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        game.gameName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(game.gameId,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.primary)
                        Text("•", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                        Text(if (game.isCd) "CD" else "DVD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                        Text("•", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                        Text(formatTransferSize(game.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
                if (activeProgress?.isDone == true) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                }
            }

            // Transfer progress
            activeProgress?.let { prog ->
                if (!prog.isDone) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Partie ${prog.currentPart}/${prog.totalParts}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("%.0f%%".format(prog.fraction * 100),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { prog.fraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
                prog.error?.let { err ->
                    Text("Erreur: $err",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ActiveTransferRow(gameId: String, progress: TransferProgress) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(gameId, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                Text("%.0f%%".format(progress.fraction * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferDestinationDialog(
    selectedCount: Int,
    otherMounts: List<MountInfo>,
    onDismiss: () -> Unit,
    onToInternal: () -> Unit,
    onToMount: (MountInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Send, null) },
        title = { Text("Destination — $selectedCount jeu(x)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choisissez où copier les jeux sélectionnés :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Internal default folder
                Card(
                    onClick = onToInternal,
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhoneAndroid, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp))
                        Column {
                            Text("Stockage interne (défaut)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Dossier UL par défaut",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }

                // Other USB mounts
                otherMounts.forEach { mount ->
                    Card(
                        onClick = { onToMount(mount) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Usb, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp))
                            Column {
                                Text(
                                    "USB: ${mount.mountPoint.substringAfterLast('/')}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(mount.fsType.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun NoUsbTransferPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Usb, null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(Modifier.height(16.dp))
        Text("Aucune clé USB détectée",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "Branchez une clé USB OTG contenant des jeux au format UL (ul.cfg + ul.*)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Si la clé est branchée, appuyez sur le bouton rafraîchir en haut.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            fontSize = 11.sp
        )
    }
}

private fun formatTransferSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f Go".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.0f Mo".format(bytes / 1_048_576.0)
    bytes >= 1024L          -> "%.0f Ko".format(bytes / 1024.0)
    else                    -> "${bytes} o"
}
