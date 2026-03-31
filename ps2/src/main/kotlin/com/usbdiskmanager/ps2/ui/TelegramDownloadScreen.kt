package com.usbdiskmanager.ps2.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.usbdiskmanager.ps2.data.download.TelegramDownloadManager
import com.usbdiskmanager.ps2.data.download.TgDownloadProgress
import com.usbdiskmanager.ps2.data.download.TgDownloadStatus
import com.usbdiskmanager.ps2.telegram.TelegramChannelConfig
import com.usbdiskmanager.ps2.telegram.TelegramGamePost
import com.usbdiskmanager.ps2.telegram.TelegramSetupState

// ──────────────────────────────────────────────────────────────────────────────
// Root screen — routes based on auth state
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramDownloadScreen(viewModel: Ps2ViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tgState = uiState.telegramState

    AnimatedContent(targetState = tgState.setupState, label = "tg_auth") { state ->
        when (state) {
            is TelegramSetupState.NotConfigured  -> TelegramCredentialsScreen(viewModel)
            is TelegramSetupState.WaitingPhoneNumber -> TelegramPhoneScreen(viewModel)
            is TelegramSetupState.WaitingCode   -> TelegramCodeScreen(viewModel, state.phoneNumber)
            is TelegramSetupState.WaitingPassword   -> TelegramPasswordScreen(viewModel)
            is TelegramSetupState.Ready         -> TelegramBrowserScreen(viewModel, tgState)
            is TelegramSetupState.Error         ->
                TelegramErrorScreen(state.message) { viewModel.disconnectTelegram() }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Auth screens (credentials / phone / code / password)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TelegramCredentialsScreen(viewModel: Ps2ViewModel) {
    var apiIdText by remember { mutableStateOf("") }
    var apiHash   by remember { mutableStateOf("") }
    var error     by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { TelegramHeader("Connexion Telegram", "Entrez vos identifiants API pour activer les téléchargements TDLib.") }

        item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comment obtenir api_id & api_hash ?", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    StepItem("1", "Allez sur my.telegram.org")
                    StepItem("2", "Connectez-vous avec votre numéro de téléphone")
                    StepItem("3", "Cliquez sur « API development tools »")
                    StepItem("4", "Créez une app (nom libre) → copiez api_id & api_hash")
                    OutlinedButton(
                        onClick = { uriHandler.openUri("https://my.telegram.org/apps") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ouvrir my.telegram.org")
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("api_id", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = apiIdText, onValueChange = { apiIdText = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Numéro entier (ex: 1234567)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Icon(Icons.Default.Numbers, null) }
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("api_hash", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = apiHash, onValueChange = { apiHash = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chaîne hexadécimale (32 chars)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Key, null) }
                )
            }
        }

        error?.let { err -> item { ErrorBanner(err) } }

        item {
            Button(
                onClick = {
                    val id = apiIdText.trim().toIntOrNull()
                    when {
                        id == null || id <= 0 -> error = "api_id invalide (nombre entier requis)"
                        apiHash.trim().length < 10 -> error = "api_hash trop court"
                        else -> viewModel.saveTelegramCredentials(id, apiHash.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgBlue)
            ) {
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Suivant", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TelegramPhoneScreen(viewModel: Ps2ViewModel) {
    var phone   by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        TelegramHeader("Numéro de téléphone", "Entrez le numéro associé à votre compte Telegram.")

        OutlinedTextField(
            value = phone, onValueChange = { phone = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ex: +33612345678") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            isError = error != null
        )
        error?.let { ErrorBanner(it) }

        Button(
            onClick = {
                val p = phone.trim()
                if (p.length < 7) { error = "Numéro trop court"; return@Button }
                loading = true
                viewModel.sendTelegramPhone(p) { err -> error = err; loading = false }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TgBlue), enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else { Icon(Icons.Default.Send, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Envoyer le code", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
        TextButton(onClick = { viewModel.disconnectTelegram() }) { Text("Annuler / Changer les credentials") }
    }
}

@Composable
private fun TelegramCodeScreen(viewModel: Ps2ViewModel, phone: String) {
    var code    by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        TelegramHeader("Code de vérification", "Un code a été envoyé à $phone via Telegram.")

        OutlinedTextField(
            value = code, onValueChange = { code = it.filter { c -> c.isDigit() }; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Code à 5 chiffres") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Icon(Icons.Default.Pin, null) }, isError = error != null
        )
        error?.let { ErrorBanner(it) }

        Button(
            onClick = {
                if (code.length < 4) { error = "Code trop court"; return@Button }
                loading = true
                viewModel.sendTelegramCode(code.trim()) { err -> error = err; loading = false }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TgBlue), enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else { Icon(Icons.Default.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Valider le code", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
        TextButton(onClick = { viewModel.disconnectTelegram() }) { Text("Recommencer") }
    }
}

@Composable
private fun TelegramPasswordScreen(viewModel: Ps2ViewModel) {
    var password by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf<String?>(null) }
    var loading  by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        TelegramHeader("Mot de passe 2FA", "Votre compte a la vérification en deux étapes activée.")

        OutlinedTextField(
            value = password, onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mot de passe cloud Telegram") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = { Icon(Icons.Default.Lock, null) }, isError = error != null
        )
        error?.let { ErrorBanner(it) }

        Button(
            onClick = {
                if (password.isBlank()) { error = "Mot de passe vide"; return@Button }
                loading = true
                viewModel.sendTelegramPassword(password) { err -> error = err; loading = false }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TgBlue), enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else { Icon(Icons.Default.LockOpen, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Se connecter", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun TelegramErrorScreen(message: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Erreur Telegram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReset) { Text("Recommencer") }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Main browser screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramBrowserScreen(viewModel: Ps2ViewModel, tgState: TelegramUiState) {
    var showAddChannel    by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<TelegramChannelConfig?>(null) }
    var showDisconnect    by remember { mutableStateOf(false) }
    var isGridView        by remember { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current

    if (showAddChannel) {
        AddChannelDialog(
            onAdd     = { u, n -> viewModel.addTelegramChannel(u, n); showAddChannel = false },
            onDismiss = { showAddChannel = false }
        )
    }
    showDeleteConfirm?.let { chan ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer le canal ?") },
            text  = { Text("@${chan.username}") },
            confirmButton = {
                Button(onClick = { viewModel.removeTelegramChannel(chan.username); showDeleteConfirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Annuler") } }
        )
    }
    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            icon  = { Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Déconnecter Telegram ?") },
            text  = { Text("La session TDLib sera supprimée.") },
            confirmButton = {
                Button(onClick = { viewModel.disconnectTelegram(); showDisconnect = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Déconnecter") }
            },
            dismissButton = { TextButton(onClick = { showDisconnect = false }) { Text("Annuler") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top bar ──
        Surface(
            color = TgBlueDark,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Telegram — Jeux PS2",
                        fontWeight = FontWeight.Bold, color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        buildString {
                            append("${tgState.channels.size} canal(aux) · ${tgState.allPosts.size} jeux")
                            if (tgState.usingTDLib) append(" · TDLib ⚡") else append(" · Web")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, null, tint = Color.White)
                }
                IconButton(onClick = { viewModel.refreshTelegramPosts() }) {
                    if (tgState.isLoading)
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
                }
                IconButton(onClick = { showDisconnect = true }) {
                    Icon(Icons.Default.Settings, null, tint = Color.White)
                }
            }
        }

        // ── Channel tabs ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tgState.channels.forEach { chan ->
                FilterChip(
                    selected = tgState.selectedChannel == chan.username,
                    onClick  = { viewModel.selectTelegramChannel(chan.username) },
                    label = {
                        Text(
                            "@${chan.username}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (tgState.selectedChannel == chan.username) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon  = { Icon(Icons.Default.Send, null, modifier = Modifier.size(12.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { showDeleteConfirm = chan }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
                        }
                    }
                )
            }
            InputChip(
                selected = false, onClick = { showAddChannel = true },
                label = { Text("+ Ajouter", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp)) }
            )
        }

        HorizontalDivider()

        // ── Error banner ──
        tgState.error?.let { err ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearTelegramError() }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        val displayPosts = if (tgState.selectedChannel.isNullOrBlank()) tgState.allPosts
                           else tgState.allPosts.filter { it.channelUsername == tgState.selectedChannel }

        if (displayPosts.isEmpty() && !tgState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Text("Aucun jeu trouvé", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.refreshTelegramPosts() }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Actualiser")
                    }
                }
            }
        } else {
            // ── Stats bar ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${displayPosts.size} jeu(x)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val archives = displayPosts.count { it.fileParts.any { p -> p.fileExtension in setOf(".rar",".7z",".zip") } }
                    if (archives > 0) {
                        Surface(color = ArchiveColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text("$archives archive(s)", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = ArchiveColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(if (tgState.usingTDLib) "TDLib ⚡" else "Web",
                        style = MaterialTheme.typography.labelSmall, color = TgBlue)
                }
            }

            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayPosts, key = { "${it.channelUsername}_${it.messageId}" }) { post ->
                        val dlId = TelegramDownloadManager.downloadId(post.channelUsername, post.messageId)
                        TelegramGameGridCard(
                            post = post,
                            downloadProgress = tgState.downloads[dlId],
                            onDownload = { viewModel.downloadTelegramGame(post) },
                            onCancel   = { viewModel.cancelTelegramDownload(dlId) },
                            onOpenTelegram = { uriHandler.openUri("https://t.me/${post.channelUsername}/${post.messageId}") }
                        )
                    }
                    item {
                        tgState.selectedChannel?.let { ch ->
                            OutlinedButton(
                                onClick = { viewModel.loadMoreTelegramPosts(ch, displayPosts.lastOrNull()?.messageId ?: 0) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Charger plus")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayPosts, key = { "${it.channelUsername}_${it.messageId}" }) { post ->
                        val dlId = TelegramDownloadManager.downloadId(post.channelUsername, post.messageId)
                        TelegramGameListCard(
                            post = post,
                            downloadProgress = tgState.downloads[dlId],
                            onDownload = { viewModel.downloadTelegramGame(post) },
                            onCancel   = { viewModel.cancelTelegramDownload(dlId) },
                            onOpenTelegram = { uriHandler.openUri("https://t.me/${post.channelUsername}/${post.messageId}") }
                        )
                    }
                    item {
                        tgState.selectedChannel?.let { ch ->
                            OutlinedButton(
                                onClick = { viewModel.loadMoreTelegramPosts(ch, displayPosts.lastOrNull()?.messageId ?: 0) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Charger plus")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Grid card (2-column)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TelegramGameGridCard(
    post: TelegramGamePost,
    downloadProgress: TgDownloadProgress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenTelegram: () -> Unit
) {
    val isActive = downloadProgress?.status == TgDownloadStatus.DOWNLOADING ||
                   downloadProgress?.status == TgDownloadStatus.QUEUED
    val isDone   = downloadProgress?.status == TgDownloadStatus.DONE
    val isError  = downloadProgress?.status == TgDownloadStatus.ERROR
    val ext      = post.fileParts.firstOrNull()?.fileExtension ?: ""
    val isArchive = ext in setOf(".rar", ".7z", ".zip")

    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // ── Cover image ──
            Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                if (!post.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.thumbnailUrl,
                        contentDescription = post.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B3A5C))),
                            RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VideogameAsset, null,
                            tint = TgBlue.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    }
                }

                // Gradient overlay at bottom for title readability
                Box(
                    modifier = Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                )

                // Title over gradient
                Text(
                    post.title.ifBlank { "PS2 #${post.messageId}" },
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )

                // Format badge (top-left)
                Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                    FormatBadge(ext)
                }

                // Action button (top-right)
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    when {
                        isDone -> Box(
                            modifier = Modifier.size(32.dp).background(Color(0xFF4CAF50), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(17.dp)) }

                        isActive -> FilledTonalIconButton(
                            onClick = onCancel, modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) { Icon(Icons.Default.Stop, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error) }

                        else -> FilledTonalIconButton(
                            onClick = onDownload, modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = TgBlue)
                        ) { Icon(Icons.Default.Download, null, modifier = Modifier.size(17.dp), tint = Color.White) }
                    }
                }

                // Multi-part badge (bottom-right of image)
                if (post.isMultiPart) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                        Surface(color = Color(0xCC000000), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "${post.fileParts.size} parties",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // ── Info zone ──
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {

                // Size + region row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(post.sizeFormatted, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    if (post.region.isNotBlank()) RegionBadge(post.region)
                }

                // Genre
                if (post.genre.isNotBlank()) {
                    Text(post.genre, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Archive notice
                if (isArchive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderZip, null, tint = ArchiveColor, modifier = Modifier.size(11.dp))
                        Text("À extraire", style = MaterialTheme.typography.labelSmall, color = ArchiveColor, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Progress
                if (isActive || isError) {
                    Spacer(Modifier.height(2.dp))
                    DownloadProgressMini(downloadProgress)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// List card (full width)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun TelegramGameListCard(
    post: TelegramGamePost,
    downloadProgress: TgDownloadProgress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenTelegram: () -> Unit
) {
    val isActive  = downloadProgress?.status == TgDownloadStatus.DOWNLOADING ||
                    downloadProgress?.status == TgDownloadStatus.QUEUED
    val isDone    = downloadProgress?.status == TgDownloadStatus.DONE
    val isError   = downloadProgress?.status == TgDownloadStatus.ERROR
    val ext       = post.fileParts.firstOrNull()?.fileExtension ?: ""
    val isArchive = ext in setOf(".rar", ".7z", ".zip")

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(modifier = Modifier.padding(10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {

                // ── Cover thumbnail ──
                Box(
                    modifier = Modifier.size(width = 58.dp, height = 74.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (!post.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = post.thumbnailUrl, contentDescription = post.title,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B3A5C)))
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideogameAsset, null,
                                tint = TgBlue.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                        }
                    }
                    // Format badge overlay
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)) {
                        FormatBadge(ext, compact = true)
                    }
                }

                // ── Content ──
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        post.title.ifBlank { "Jeu PS2 #${post.messageId}" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )

                    // Badges row: region + serial
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (post.region.isNotBlank()) RegionBadge(post.region)
                        if (post.gameId.isNotBlank()) {
                            Text(post.gameId, style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Genre + publisher
                    if (post.genre.isNotBlank() || post.publisher.isNotBlank()) {
                        Text(
                            listOf(post.genre, post.publisher).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Size + multi-part + archive notice
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(post.sizeFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        if (post.isMultiPart) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(3.dp)) {
                                Text("${post.fileParts.size} parties",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                            }
                        }
                        if (isArchive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderZip, null, tint = ArchiveColor, modifier = Modifier.size(11.dp))
                                Text("À extraire", style = MaterialTheme.typography.labelSmall, color = ArchiveColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Done state
                    if (isDone) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                            Text("Téléchargé", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                        }
                    }

                    // Progress
                    if (isActive || isError) {
                        DownloadProgressMini(downloadProgress)
                    }
                }

                // ── Actions ──
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isDone && !isActive) {
                        FilledTonalIconButton(
                            onClick = onDownload, modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = TgBlue.copy(alpha = 0.15f))
                        ) { Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp), tint = TgBlue) }
                    }
                    if (isActive) {
                        FilledTonalIconButton(
                            onClick = onCancel, modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) { Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                    }
                    FilledTonalIconButton(onClick = onOpenTelegram, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Reusable sub-composables
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun DownloadProgressMini(prog: TgDownloadProgress?) {
    if (prog == null) return
    when (prog.status) {
        TgDownloadStatus.ERROR -> {
            Text("Erreur: ${prog.error}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TgDownloadStatus.QUEUED -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                Text("En attente…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("%.0f%%".format(prog.fraction * 100),
                        style = MaterialTheme.typography.labelSmall, color = TgBlue, fontWeight = FontWeight.Bold)
                    Text(prog.speedFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                LinearProgressIndicator(
                    progress = { prog.fraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = TgBlue
                )
                if (prog.etaSeconds > 0) {
                    val eta = if (prog.etaSeconds > 60) "${prog.etaSeconds / 60}m ${prog.etaSeconds % 60}s" else "${prog.etaSeconds}s"
                    Text("ETA: $eta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun FormatBadge(ext: String, compact: Boolean = false) {
    val (label, bgColor) = when (ext) {
        ".iso", ".bin", ".chd" -> "ISO"   to Color(0xFF2E7D32)
        ".7z"                  -> "7Z"    to Color(0xFFE65100)
        ".rar"                 -> "RAR"   to Color(0xFFC62828)
        ".zip"                 -> "ZIP"   to Color(0xFF1565C0)
        else                   -> ext.removePrefix(".").uppercase().take(4) to Color(0xFF424242)
    }
    Surface(color = bgColor, shape = RoundedCornerShape(if (compact) 3.dp else 4.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = if (compact) 3.dp else 5.dp, vertical = if (compact) 1.dp else 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 8.sp else 9.sp
        )
    }
}

@Composable
private fun RegionBadge(region: String) {
    val (label, bgColor) = when {
        region.contains("NTSC-U", ignoreCase = true) ||
        region.contains("USA",    ignoreCase = true) -> "USA" to Color(0xFF1565C0)
        region.contains("PAL",    ignoreCase = true) ||
        region.contains("Europe", ignoreCase = true) -> "EU"  to Color(0xFF2E7D32)
        region.contains("NTSC-J", ignoreCase = true) ||
        region.contains("Japan",  ignoreCase = true) -> "JPN" to Color(0xFFC62828)
        else -> region.take(5) to Color(0xFF555555)
    }
    Surface(color = bgColor, shape = RoundedCornerShape(4.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun TelegramHeader(title: String, subtitle: String) {
    Surface(color = Color(0xFF1A237E).copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp)) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(TgBlueDark, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StepItem(num: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Surface(color = TgBlue, shape = RoundedCornerShape(50)) {
            Text(num, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChannelDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var name     by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un canal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username, onValueChange = { username = it; error = null },
                    label = { Text("Username (@pcsx2iso)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Send, null) }
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nom affiché (optionnel)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { ErrorBanner(it) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = username.trim().removePrefix("@")
                if (clean.length < 3) { error = "Username trop court"; return@Button }
                onAdd(clean, name.trim())
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Design tokens
// ──────────────────────────────────────────────────────────────────────────────

private val TgBlue     = Color(0xFF0088CC)
private val TgBlueDark = Color(0xFF006BA6)
private val ArchiveColor = Color(0xFFE65100)
