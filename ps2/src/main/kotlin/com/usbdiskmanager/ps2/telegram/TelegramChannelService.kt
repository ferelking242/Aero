package com.usbdiskmanager.ps2.telegram

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// ─── Domain models ────────────────────────────────────────────────────────────

data class TelegramChannelConfig(
    val username: String,
    val displayName: String,
    val id: Long = 0L,
    val accessHash: Long = 0L
)

data class TelegramGamePost(
    val messageId: Int,
    val channelUsername: String,
    val title: String,
    val description: String,
    val region: String,
    val gameId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val documentId: Long = 0L,
    val accessHash: Long = 0L,
    val fileReference: ByteArray = ByteArray(0),
    val dcId: Int = 0,
    val coverPhotoId: Long = 0L,
    val thumbnailUrl: String? = null,
    // TDLib native fields (populated when fetched via TDLib, 0 when web-scraped)
    val tdlibMessageId: Long = 0L,
    val tdlibChatId: Long = 0L,
    val thumbnailFileId: Int = 0,
    val date: Int = 0
) {
    val isIso
        get() = mimeType.contains("iso", ignoreCase = true) ||
            fileName.endsWith(".iso", ignoreCase = true) ||
            fileName.endsWith(".bin", ignoreCase = true) ||
            fileName.endsWith(".7z", ignoreCase = true) ||
            fileName.endsWith(".zip", ignoreCase = true)

    val sizeFormatted
        get() = when {
            fileSizeBytes > 1_000_000_000L -> "%.1f GB".format(fileSizeBytes / 1e9)
            fileSizeBytes > 1_000_000L -> "%.0f MB".format(fileSizeBytes / 1e6)
            fileSizeBytes > 0L -> "%.0f KB".format(fileSizeBytes / 1e3)
            else -> "?"
        }

    override fun equals(other: Any?) =
        other is TelegramGamePost &&
            messageId == other.messageId &&
            channelUsername == other.channelUsername

    override fun hashCode() = 31 * messageId + channelUsername.hashCode()
}

// ─── Auth states ──────────────────────────────────────────────────────────────

sealed class TelegramSetupState {
    object NotConfigured : TelegramSetupState()
    object WaitingPhoneNumber : TelegramSetupState()
    data class WaitingCode(val phoneNumber: String) : TelegramSetupState()
    object WaitingPassword : TelegramSetupState()
    object Ready : TelegramSetupState()
    data class Error(val message: String) : TelegramSetupState()
}

// ─── Prefs ────────────────────────────────────────────────────────────────────

private const val PREFS_NAME = "telegram_prefs"
private const val KEY_API_ID = "api_id"
private const val KEY_API_HASH = "api_hash"
private const val KEY_CHANNELS = "channels"
private const val KEY_PHONE = "phone"

private val DEFAULT_CHANNELS = listOf(
    TelegramChannelConfig("pcsx2iso", "Playstation 2 Roms"),
    TelegramChannelConfig("ps2isodl", "PS2 ISO Downloads"),
    TelegramChannelConfig("PSXGames", "PSX / PS2 Games")
)

// ─── Service ──────────────────────────────────────────────────────────────────

@Singleton
class TelegramChannelService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tdlib: TDLibClient
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val tdlibAuthState: StateFlow<TdApi.AuthorizationState?> get() = tdlib.authState

    // ── Credentials ───────────────────────────────────────────────────────────

    fun getSavedApiId(): Int = prefs.getInt(KEY_API_ID, 0)
    fun getSavedApiHash(): String = prefs.getString(KEY_API_HASH, "") ?: ""
    fun getSavedPhone(): String = prefs.getString(KEY_PHONE, "") ?: ""

    fun saveCredentials(apiId: Int, apiHash: String) {
        prefs.edit {
            putInt(KEY_API_ID, apiId)
            putString(KEY_API_HASH, apiHash.trim())
        }
        tdlib.initialize(apiId, apiHash.trim())
    }

    fun initializeTDLib() {
        val apiId = getSavedApiId()
        val apiHash = getSavedApiHash()
        if (apiId > 0 && apiHash.isNotBlank()) {
            tdlib.initialize(apiId, apiHash)
        }
    }

    fun getSetupState(): TelegramSetupState {
        if (getSavedApiId() <= 0 || getSavedApiHash().isBlank()) {
            return TelegramSetupState.NotConfigured
        }
        return when (tdlib.authState.value) {
            is TdApi.AuthorizationStateReady -> TelegramSetupState.Ready
            is TdApi.AuthorizationStateWaitPhoneNumber,
            is TdApi.AuthorizationStateWaitTdlibParameters ->
                TelegramSetupState.WaitingPhoneNumber
            is TdApi.AuthorizationStateWaitCode ->
                TelegramSetupState.WaitingCode(getSavedPhone())
            is TdApi.AuthorizationStateWaitPassword ->
                TelegramSetupState.WaitingPassword
            null -> if (getSavedApiId() > 0) TelegramSetupState.WaitingPhoneNumber
            else TelegramSetupState.NotConfigured
            else -> TelegramSetupState.WaitingPhoneNumber
        }
    }

    suspend fun sendPhone(phone: String): Result<Unit> = runCatching {
        prefs.edit { putString(KEY_PHONE, phone.trim()) }
        tdlib.setPhoneNumber(phone.trim())
    }

    suspend fun sendCode(code: String): Result<Unit> = runCatching {
        tdlib.checkCode(code.trim())
    }

    suspend fun sendPassword(password: String): Result<Unit> = runCatching {
        tdlib.checkPassword(password)
    }

    suspend fun logOut() {
        runCatching { tdlib.logOut() }
        prefs.edit { remove(KEY_PHONE) }
    }

    fun clearSetup() {
        prefs.edit {
            remove(KEY_API_ID)
            remove(KEY_API_HASH)
            remove(KEY_PHONE)
        }
        tdlib.close()
    }

    // ── Channel management ────────────────────────────────────────────────────

    fun getSavedChannels(): List<TelegramChannelConfig> {
        val raw = prefs.getString(KEY_CHANNELS, null)
        if (raw == null) return DEFAULT_CHANNELS
        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size >= 2) TelegramChannelConfig(
                parts[0], parts[1],
                parts.getOrNull(2)?.toLongOrNull() ?: 0L,
                parts.getOrNull(3)?.toLongOrNull() ?: 0L
            )
            else null
        }.ifEmpty { DEFAULT_CHANNELS }
    }

    fun saveChannels(channels: List<TelegramChannelConfig>) {
        val raw = channels.joinToString("|") {
            "${it.username},${it.displayName},${it.id},${it.accessHash}"
        }
        prefs.edit { putString(KEY_CHANNELS, raw) }
    }

    fun addChannel(username: String, name: String) {
        val current = getSavedChannels().toMutableList()
        val clean = username.removePrefix("@").removePrefix("https://t.me/").trim()
        if (current.none { it.username == clean }) {
            current.add(TelegramChannelConfig(clean, name.ifBlank { "@$clean" }))
            saveChannels(current)
        }
    }

    fun removeChannel(username: String) {
        saveChannels(getSavedChannels().filter { it.username != username })
    }

    // ── TDLib native channel indexing (when authenticated) ────────────────────

    /**
     * Fetch up to [limit] game posts from a channel using TDLib directly.
     * [fromTdlibId] = 0 means "start from the latest message".
     * Returns posts sorted newest-first.
     */
    suspend fun fetchChannelPostsTDLib(
        channelUsername: String,
        fromTdlibId: Long = 0L,
        limit: Int = 50
    ): List<TelegramGamePost> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val chat = tdlib.searchPublicChat(channelUsername)
        val messages = tdlib.getChatHistory(chat.id, fromTdlibId, limit)
        val result = mutableListOf<TelegramGamePost>()
        for (msg in messages.messages ?: emptyArray()) {
            parseTdlibMessage(msg, channelUsername, chat.id)?.let { result.add(it) }
        }
        result
    }

    private fun parseTdlibMessage(
        msg: TdApi.Message,
        channelUsername: String,
        chatId: Long
    ): TelegramGamePost? {
        val docContent = msg.content as? TdApi.MessageDocument ?: return null
        val doc = docContent.document
        val fileName = doc.fileName?.takeIf { it.isNotBlank() } ?: return null
        if (GAME_EXTENSIONS.none { fileName.endsWith(it, ignoreCase = true) }) return null

        val caption = docContent.caption?.text ?: ""
        if (isSpam(caption)) return null

        // In TDLib, channel message IDs are: postNumber << 20
        val urlPostNumber = (msg.id ushr 20).toInt().coerceAtLeast(msg.id.toInt())

        // Thumbnail: use cached local path if already downloaded, otherwise store fileId
        val thumb = doc.thumbnail
        val thumbFileId = thumb?.file?.id ?: 0
        val thumbLocal = thumb?.file?.local
        val thumbUrl = if (thumbLocal != null && thumbLocal.isDownloadingCompleted && thumbLocal.path.isNotBlank())
            "file://${thumbLocal.path}"
        else null

        val combined = "$caption $fileName"
        return TelegramGamePost(
            messageId = urlPostNumber,
            channelUsername = channelUsername,
            title = extractTitle(caption, fileName),
            description = caption.take(300),
            region = extractRegion(combined),
            gameId = extractGameId(combined),
            fileName = fileName,
            fileSizeBytes = doc.document.size,
            mimeType = doc.mimeType ?: "application/octet-stream",
            documentId = doc.document.id.toLong(),
            thumbnailUrl = thumbUrl,
            thumbnailFileId = thumbFileId,
            tdlibMessageId = msg.id,
            tdlibChatId = chatId,
            date = msg.date
        )
    }

    /**
     * Downloads a TDLib file (thumbnail) and returns its local file path,
     * or null if it fails or is already not available.
     */
    suspend fun downloadThumbnail(fileId: Int): String? = runCatching {
        val file = tdlib.startDownload(fileId, priority = 1)
        if (file.local.isDownloadingCompleted && file.local.path.isNotBlank())
            return file.local.path
        // Wait for the file to finish downloading via file update events
        val update = tdlib.fileUpdates
            .first { it.file.id == fileId && it.file.local.isDownloadingCompleted }
        update.file.local.path.takeIf { it.isNotBlank() }
    }.getOrNull()

    // ── Web scraping (browse, no auth) ────────────────────────────────────────

    suspend fun fetchChannelPostsWeb(
        channelUsername: String,
        beforeId: Int = 0
    ): List<TelegramGamePost> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append("https://t.me/s/$channelUsername")
                if (beforeId > 0) append("?before=$beforeId")
            }
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; UsbDiskManager)")
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseWebChannelHtml(html, channelUsername)
        } catch (e: Exception) {
            Timber.e(e, "fetchChannelPostsWeb failed for $channelUsername")
            emptyList()
        }
    }

    private fun parseWebChannelHtml(
        html: String,
        channelUsername: String
    ): List<TelegramGamePost> {
        val posts = mutableListOf<TelegramGamePost>()

        val msgPattern = Regex(
            """data-post="$channelUsername/(\d+)"[\s\S]*?class="tgme_widget_message_bubble"([\s\S]*?)(?=data-post=|$)"""
        )
        val textPattern =
            Regex("""class="tgme_widget_message_text[^"]*"[^>]*>([\s\S]*?)</div>""")
        val docNamePattern =
            Regex("""tgme_widget_message_document_title[^>]*>([^<]+)<""")
        val docSizePattern =
            Regex("""tgme_widget_message_document_extra[^>]*>([^<]+)<""")
        val photoPattern =
            Regex("""tgme_widget_message_photo_wrap[^"]*"[^>]*style="background-image:url\('([^']+)'\)""")

        for (match in msgPattern.findAll(html)) {
            val msgId = match.groupValues[1].toIntOrNull() ?: continue
            val body = match.groupValues[2]

            val docName = docNamePattern.find(body)?.groupValues?.get(1)?.trim() ?: ""

            // RÈGLE 1 : doit avoir un fichier attaché avec une extension de jeu reconnue
            if (docName.isBlank()) continue
            if (GAME_EXTENSIONS.none { docName.endsWith(it, ignoreCase = true) }) continue

            val rawText = textPattern.find(body)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]+>"), " ")
                ?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")
                ?.replace("&nbsp;", " ")?.trim() ?: ""
            val docSize = docSizePattern.find(body)?.groupValues?.get(1)?.trim() ?: ""

            // RÈGLE 2 : exclure le spam évident (promos, pubs, vente, etc.)
            if (isSpam(rawText)) continue

            val title = extractTitle(rawText, docName)
            val region = extractRegion(rawText + " " + docName)
            val gameId = extractGameId(rawText + " " + docName)
            val sizeBytes = parseSizeText(docSize)
            val mime = when {
                docName.endsWith(".iso", true) -> "application/x-iso9660-image"
                docName.endsWith(".bin", true) -> "application/octet-stream"
                docName.endsWith(".7z", true) -> "application/x-7z-compressed"
                docName.endsWith(".zip", true) -> "application/zip"
                else -> "application/octet-stream"
            }
            val thumbUrl = photoPattern.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

            posts.add(
                TelegramGamePost(
                    messageId = msgId,
                    channelUsername = channelUsername,
                    title = title,
                    description = rawText.take(300),
                    region = region,
                    gameId = gameId,
                    fileName = docName,
                    fileSizeBytes = sizeBytes,
                    mimeType = mime,
                    thumbnailUrl = thumbUrl,
                    date = 0
                )
            )
        }
        return posts
    }

    // ── Deep link ─────────────────────────────────────────────────────────────

    fun getTelegramDeepLink(channelUsername: String, messageId: Int) =
        "https://t.me/$channelUsername/$messageId"

    // ── Content helpers ───────────────────────────────────────────────────────

    private fun extractTitle(caption: String, fileName: String): String {
        val fromCaption = caption
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.replace(Regex("<[^>]+>"), "")
            ?.take(80)
            ?.trim()
        if (!fromCaption.isNullOrBlank()) return fromCaption
        return fileName
            .removeSuffix(".iso").removeSuffix(".bin")
            .removeSuffix(".7z").removeSuffix(".zip")
            .replace("_", " ").replace("-", " ")
            .trim().take(80)
    }

    private fun extractRegion(text: String): String {
        val t = text.lowercase()
        return when {
            t.contains("(usa)") || t.contains("ntsc-u") || t.contains("slus") -> "NTSC-U"
            t.contains("(europe)") || t.contains("pal") || t.contains("sles") -> "PAL"
            t.contains("(japan)") || t.contains("ntsc-j") ||
                t.contains("slps") || t.contains("slpm") -> "NTSC-J"
            else -> ""
        }
    }

    private fun extractGameId(text: String): String =
        PS2_ID_REGEX.find(text)?.value?.replace("_", "-") ?: ""

    private fun parseSizeText(sizeStr: String): Long {
        val cleaned = sizeStr.trim()
        val num = Regex("([\\d.]+)").find(cleaned)?.groupValues?.get(1)
            ?.toDoubleOrNull() ?: return 0L
        return when {
            cleaned.contains("GB", ignoreCase = true) -> (num * 1_000_000_000).toLong()
            cleaned.contains("MB", ignoreCase = true) -> (num * 1_000_000).toLong()
            cleaned.contains("KB", ignoreCase = true) -> (num * 1_000).toLong()
            else -> 0L
        }
    }

    companion object {
        private val GAME_EXTENSIONS = listOf(".iso", ".bin", ".7z", ".zip", ".rar", ".chd")

        private val SPAM_KEYWORDS = listOf(
            "promo code", "gift card", "dm me", "₹", "rs ", "discount",
            "selling", "only one member", "interested people", "gift",
            "coupon", "offer", "serum", "toner", "product", "shipping",
            "whatsapp", "call me", "click here", "buy now", "free",
            "earn money", "investment", "crypto", "bitcoin"
        )

        private val PS2_ID_REGEX = Regex(
            "(SLES|SCES|SLUS|SCUS|SLED|SCED|SLPM|SLPS|SCPS|SCAJ)[_-]?\\d{3}\\.\\d{2}",
            RegexOption.IGNORE_CASE
        )
    }

    private fun isSpam(text: String): Boolean {
        if (text.isBlank()) return false
        val t = text.lowercase()
        return SPAM_KEYWORDS.any { t.contains(it) }
    }
}
