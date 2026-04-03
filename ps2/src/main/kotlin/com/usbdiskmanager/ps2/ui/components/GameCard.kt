package com.usbdiskmanager.ps2.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.usbdiskmanager.ps2.data.cover.CoverType
import com.usbdiskmanager.ps2.domain.model.ConversionProgress
import com.usbdiskmanager.ps2.domain.model.ConversionStatus
import com.usbdiskmanager.ps2.domain.model.Ps2Game
import java.io.File

@Composable
fun GameCard(
    game: Ps2Game,
    progress: ConversionProgress?,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onConvertClick: () -> Unit,
    onSelectClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onFetchCoverClick: () -> Unit,
    onFetchCoverWithType: ((CoverType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else
            Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bg"
    )

    var showCoverTypeMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(
                if (isSelected) Modifier.border(2.dp, borderColor, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.background(bgColor)) {
            Column(modifier = Modifier.padding(12.dp)) {
                // ── Top row: cover + info ────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isMultiSelectMode) {
                        Box(
                            modifier = Modifier
                                .size(56.dp, 78.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .clickable(onClick = onSelectClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isSelected) Icons.Default.CheckCircle
                                else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) Color.White
                                       else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Box {
                            CoverThumbnail(
                                game = game,
                                onFetchCoverClick = {
                                    if (onFetchCoverWithType != null) showCoverTypeMenu = true
                                    else onFetchCoverClick()
                                }
                            )
                            // Cover type dropdown
                            if (onFetchCoverWithType != null) {
                                DropdownMenu(
                                    expanded = showCoverTypeMenu,
                                    onDismissRequest = { showCoverTypeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Standard (Default)") },
                                        leadingIcon = { Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showCoverTypeMenu = false
                                            onFetchCoverWithType(CoverType.DEFAULT)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("2D (Alternative)") },
                                        leadingIcon = { Icon(Icons.Default.CropOriginal, null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showCoverTypeMenu = false
                                            onFetchCoverWithType(CoverType.TWO_D)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("3D (Boîte)") },
                                        leadingIcon = { Icon(Icons.Default.ViewInAr, null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showCoverTypeMenu = false
                                            onFetchCoverWithType(CoverType.THREE_D)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        StatusBadge(game.conversionStatus)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (game.gameId.isNotBlank()) {
                            Text(
                                text = game.gameId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RegionChip(game.region)
                            SizeChip(game.sizeMb)
                        }
                    }
                }

                // ── Progress bar ─────────────────────────────────────────────────
                if (progress != null && !isMultiSelectMode) {
                    Spacer(Modifier.height(8.dp))
                    ConversionProgressRow(progress)
                }

                // ── Action buttons row ────────────────────────────────────────────
                if (!isMultiSelectMode) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Convert / Pause / Resume / Done
                        when {
                            progress != null -> {
                                FilledTonalButton(
                                    onClick = onPauseClick,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pause", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = onCancelClick,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Annuler", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            game.conversionStatus == ConversionStatus.PAUSED ||
                            game.conversionStatus == ConversionStatus.ERROR -> {
                                Button(
                                    onClick = onResumeClick,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reprendre", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = onCancelClick,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Annuler", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            game.conversionStatus != ConversionStatus.COMPLETED -> {
                                Button(
                                    onClick = onConvertClick,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Transform, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Convertir", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            else -> {
                                FilledTonalButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                        contentColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Converti", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Select button
                        OutlinedButton(
                            onClick = onSelectClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CheckBoxOutlineBlank, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sélect.", style = MaterialTheme.typography.labelSmall)
                        }

                        // Delete button
                        OutlinedButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Suppr.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameGridCard(
    game: Ps2Game,
    progress: ConversionProgress?,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onSelectClick: () -> Unit,
    onConvertClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onFetchCoverClick: () -> Unit,
    onFetchCoverWithType: ((CoverType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "border"
    )

    var showCoverTypeMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, borderColor, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Cover image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = {
                        if (onFetchCoverWithType != null) showCoverTypeMenu = true
                        else onFetchCoverClick()
                    }),
                contentAlignment = Alignment.Center
            ) {
                if (game.coverPath != null && File(game.coverPath).exists()) {
                    AsyncImage(
                        model = File(game.coverPath),
                        contentDescription = game.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideogameAsset,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Appuyer pour cover",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // Cover type dropdown anchored to the cover
                DropdownMenu(
                    expanded = showCoverTypeMenu,
                    onDismissRequest = { showCoverTypeMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Standard (Default)") },
                        leadingIcon = { Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showCoverTypeMenu = false
                            onFetchCoverWithType?.invoke(CoverType.DEFAULT) ?: onFetchCoverClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("2D (Alternative)") },
                        leadingIcon = { Icon(Icons.Default.CropOriginal, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showCoverTypeMenu = false
                            onFetchCoverWithType?.invoke(CoverType.TWO_D) ?: onFetchCoverClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("3D (Boîte)") },
                        leadingIcon = { Icon(Icons.Default.ViewInAr, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showCoverTypeMenu = false
                            onFetchCoverWithType?.invoke(CoverType.THREE_D) ?: onFetchCoverClick()
                        }
                    )
                }

                // Multi-select overlay
                if (isMultiSelectMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                            .clickable(onClick = onSelectClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isSelected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Status badge
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = CircleShape,
                    color = when (game.conversionStatus) {
                        ConversionStatus.COMPLETED    -> Color(0xFF4CAF50)
                        ConversionStatus.IN_PROGRESS  -> MaterialTheme.colorScheme.primary
                        ConversionStatus.ERROR        -> MaterialTheme.colorScheme.error
                        ConversionStatus.PAUSED       -> MaterialTheme.colorScheme.tertiary
                        ConversionStatus.NOT_CONVERTED -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    }
                ) {
                    Icon(
                        when (game.conversionStatus) {
                            ConversionStatus.COMPLETED    -> Icons.Default.CheckCircle
                            ConversionStatus.IN_PROGRESS  -> Icons.Default.Sync
                            ConversionStatus.ERROR        -> Icons.Default.ErrorOutline
                            ConversionStatus.PAUSED       -> Icons.Default.Pause
                            ConversionStatus.NOT_CONVERTED -> Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp).padding(2.dp)
                    )
                }
            }

            // Progress bar (if active)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.percent },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            // Info
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                if (game.gameId.isNotBlank()) {
                    Text(
                        text = game.gameId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(6.dp))
                RegionChip(game.region)

                if (!isMultiSelectMode) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Convert button (only if not done)
                        if (game.conversionStatus != ConversionStatus.COMPLETED) {
                            FilledTonalButton(
                                onClick = onConvertClick,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Transform, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Conv.", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.12f),
                                    contentColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                            }
                        }

                        // Select button
                        OutlinedButton(
                            onClick = onSelectClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CheckBoxOutlineBlank, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Sél.", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }

                        // Delete button
                        OutlinedButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Supp.", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverThumbnail(game: Ps2Game, onFetchCoverClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 88.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onFetchCoverClick),
        contentAlignment = Alignment.Center
    ) {
        if (game.coverPath != null && File(game.coverPath).exists()) {
            AsyncImage(
                model = File(game.coverPath),
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "COVER",
                    fontSize = 7.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ConversionStatus) {
    val (color, label) = when (status) {
        ConversionStatus.NOT_CONVERTED -> MaterialTheme.colorScheme.outline to "Non converti"
        ConversionStatus.IN_PROGRESS   -> MaterialTheme.colorScheme.primary to "En cours"
        ConversionStatus.PAUSED        -> MaterialTheme.colorScheme.tertiary to "En pause"
        ConversionStatus.COMPLETED     -> Color(0xFF4CAF50) to "Converti"
        ConversionStatus.ERROR         -> MaterialTheme.colorScheme.error to "Erreur"
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun RegionChip(region: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = region,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SizeChip(sizeBytes: Long) {
    val formatted = when {
        sizeBytes >= 1_073_741_824L -> "%.1f GB".format(sizeBytes / 1_073_741_824.0)
        sizeBytes >= 1_048_576L     -> "%.0f MB".format(sizeBytes / 1_048_576.0)
        else                         -> "${sizeBytes / 1024} KB"
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = formatted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ConversionProgressRow(progress: ConversionProgress) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "%.1f%%  %.1f MB/s".format(progress.percent * 100, progress.speedMbps),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatRemaining(progress.remainingSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.percent },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

private fun formatRemaining(seconds: Long): String = when {
    seconds == Long.MAX_VALUE || seconds < 0 -> "--:--"
    seconds >= 3600 -> "%dh %02dm".format(seconds / 3600, (seconds % 3600) / 60)
    seconds >= 60   -> "%dm %02ds".format(seconds / 60, seconds % 60)
    else            -> "${seconds}s"
}
