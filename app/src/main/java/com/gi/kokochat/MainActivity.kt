package com.gi.kokochat

import java.io.File
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.graphics.BitmapFactory
import android.app.Activity
import android.os.Bundle
import android.net.Uri
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.util.UUID
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Bg = Color(0xFF100B12)
private val Panel = Color(0xFF211722)
private val Gold = Color(0xFFE8C36A)
private val White = Color(0xFFFFF5E5)
private val Muted = Color(0xFFB9A993)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(
            primary = Gold, background = Bg, surface = Panel,
            onSurface = White, onBackground = White
        )) { ChatApp(this) } }
    }
}

private data class ChatMessage(val role: String, val content: String)

private data class ApiResult(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val rateLimitSummary: String
)

private object SecureKey {
    private const val ALIAS = "gi_koko_openai_key_v1"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$data"
    }

    fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        return try {
            val parts = value.split(":", limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("gi_koko_chat", Context.MODE_PRIVATE)

private fun loadHistory(context: Context): List<ChatMessage> {
    return try {
        val array = JSONArray(prefs(context).getString("history", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val role = obj.optString("role")
            if (role != "user" && role != "assistant") return@mapNotNull null
            ChatMessage(role, obj.optString("content"))
        }.takeLast(200)
    } catch (_: Exception) { emptyList() }
}

private data class ChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage>
)

private const val WELCOME_MESSAGE =
    "မင်္ဂလာပါ။ G.I KO KO CHAT မှ ကြိုဆိုပါတယ်။ Settings ထဲမှာ OpenAI API key ထည့်ပြီး စတင်အသုံးပြုနိုင်ပါတယ်။"

private fun sessionTitle(messages: List<ChatMessage>): String {
    val firstUser = messages.firstOrNull { it.role == "user" }?.content
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
    if (firstUser.isBlank()) return "New chat"
    return if (firstUser.length > 36) firstUser.take(36).trimEnd() + "…" else firstUser
}

private fun saveSessions(context: Context, sessions: List<ChatSession>) {
    val array = JSONArray()

    sessions.take(50).forEach { session ->
        val messageArray = JSONArray()

        session.messages.takeLast(200).forEach { message ->
            messageArray.put(
                JSONObject()
                    .put("role", message.role)
                    .put("content", message.content)
            )
        }

        array.put(
            JSONObject()
                .put("id", session.id)
                .put("title", session.title)
                .put("updatedAt", session.updatedAt)
                .put("messages", messageArray)
        )
    }

    prefs(context)
        .edit()
        .putString("sessions_v1", array.toString())
        .apply()
}

private fun loadSessions(context: Context): List<ChatSession> {
    val stored = prefs(context).getString("sessions_v1", null)

    if (!stored.isNullOrBlank()) {
        try {
            val array = JSONArray(stored)

            val loaded = (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i)
                    ?: return@mapNotNull null

                val id = obj.optString("id")
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val rawMessages = obj.optJSONArray("messages")
                    ?: JSONArray()

                val parsedMessages = (0 until rawMessages.length()).mapNotNull { j ->
                    val item = rawMessages.optJSONObject(j)
                        ?: return@mapNotNull null

                    val role = item.optString("role")
                    if (role != "user" && role != "assistant") {
                        return@mapNotNull null
                    }

                    ChatMessage(role, item.optString("content"))
                }.takeLast(200)

                ChatSession(
                    id = id,
                    title = obj.optString("title").ifBlank { "New chat" },
                    updatedAt = obj.optLong("updatedAt", 0L),
                    messages = parsedMessages
                )
            }.sortedByDescending { it.updatedAt }.take(50)

            if (loaded.isNotEmpty()) return loaded
        } catch (_: Exception) {
            // Fall back to legacy history if session data is invalid.
        }
    }

    val oldMessages = loadHistory(context)

    val migrated = ChatSession(
        id = UUID.randomUUID().toString(),
        title = sessionTitle(oldMessages),
        updatedAt = System.currentTimeMillis(),
        messages = oldMessages.ifEmpty {
            listOf(ChatMessage("assistant", WELCOME_MESSAGE))
        }
    )

    saveSessions(context, listOf(migrated))
    return listOf(migrated)
}

private fun saveHistory(context: Context, messages: List<ChatMessage>) {
    // Keep the old history as a compatibility backup.
    val array = JSONArray()
    messages.takeLast(200).forEach {
        array.put(JSONObject().put("role", it.role).put("content", it.content))
    }
    prefs(context).edit().putString("history", array.toString()).apply()
}


private data class GeneratedImage(
    val path: String,
    val prompt: String,
    val createdAt: Long
)

private fun loadGeneratedImages(context: Context): List<GeneratedImage> {
    val dir = File(context.filesDir, "gi_images")
    if (!dir.exists()) return emptyList()

    return dir.listFiles()
        ?.filter { it.isFile && it.extension.equals("png", true) }
        ?.sortedByDescending { it.lastModified() }
        ?.take(30)
        ?.map { file ->
            val promptFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
            GeneratedImage(
                path = file.absolutePath,
                prompt = try {
                    promptFile.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    ""
                },
                createdAt = file.lastModified()
            )
        }
        .orEmpty()
}

private fun normalizeImageToPng(bytes: ByteArray): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IOException("Image API returned invalid or unsupported image data.")

    try {
        val output = ByteArrayOutputStream()
        val success = bitmap.compress(
            android.graphics.Bitmap.CompressFormat.PNG,
            100,
            output
        )
        if (!success) {
            throw IOException("Could not convert the generated image to PNG.")
        }
        return output.toByteArray()
    } finally {
        bitmap.recycle()
    }
}

private fun generateImageOpenAI(
    base: String,
    apiKey: String,
    model: String,
    prompt: String
): ByteArray {
    val endpoint = base.trimEnd('/') + "/images/generations"
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 30000
        readTimeout = 180000
        doOutput = true
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
    }

    try {
        val body = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("n", 1)
            .put("size", "1024x1024")

        connection.outputStream.use {
            it.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream
                     else connection.errorStream
        val responseText = stream?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }.orEmpty()

        if (status !in 200..299) {
            val message = try {
                JSONObject(responseText).optJSONObject("error")
                    ?.optString("message")
            } catch (_: Exception) {
                null
            }
            throw IOException(
                "Image API HTTP $status: " +
                    (message?.takeIf { it.isNotBlank() }
                        ?: responseText.take(600))
            )
        }

        val item = JSONObject(responseText)
            .optJSONArray("data")
            ?.optJSONObject(0)
            ?: throw IOException("Image API response has no data[0].")

        val encoded = item.optString("b64_json")
        if (encoded.isNotBlank() && encoded != "null") {
            return normalizeImageToPng(Base64.decode(encoded, Base64.DEFAULT))
        }

        val imageUrl = item.optString("url")
        if (imageUrl.isNullOrBlank() || imageUrl == "null") {
            throw IOException(
                "Image API returned neither b64_json nor url. " +
                    "Check whether this provider supports the Images API response format."
            )
        }

        val imageConnection =
            (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 90000
            }

        try {
            val imageStatus = imageConnection.responseCode
            if (imageStatus !in 200..299) {
                throw IOException("Image download HTTP $imageStatus")
            }
            return imageConnection.inputStream.use { input ->
                normalizeImageToPng(input.readBytes())
            }
        } finally {
            imageConnection.disconnect()
        }
    } finally {
        connection.disconnect()
    }
}

private fun requestOpenAI(
    base: String,
    apiKey: String,
    model: String,
    systemPrompt: String,
    history: List<ChatMessage>
): ApiResult {
    val url = URL(base.trimEnd('/') + "/chat/completions")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20000
        readTimeout = 60000
        doOutput = true
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
    }

    try {
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                history.takeLast(40).forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
            .put("temperature", 0.7)

        connection.outputStream.use {
            it.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream
                     else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

        if (status !in 200..299) {
            val message = try {
                JSONObject(text).optJSONObject("error")?.optString("message")
            } catch (_: Exception) { null }
            throw IOException("HTTP $status: ${message?.takeIf { it.isNotBlank() } ?: text.take(500)}")
        }

        val result = JSONObject(text)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
            .orEmpty()

        if (result.isBlank()) throw IOException("API returned an empty response.")

        val usage = JSONObject(text).optJSONObject("usage")
        val promptTokens = usage?.takeIf {
            it.has("prompt_tokens") && !it.isNull("prompt_tokens")
        }?.optInt("prompt_tokens")
        val completionTokens = usage?.takeIf {
            it.has("completion_tokens") && !it.isNull("completion_tokens")
        }?.optInt("completion_tokens")
        val totalTokens = usage?.takeIf {
            it.has("total_tokens") && !it.isNull("total_tokens")
        }?.optInt("total_tokens")

        val headerNames = listOf(
            "x-ratelimit-remaining-requests" to "Requests remaining",
            "x-ratelimit-limit-requests" to "Request limit",
            "x-ratelimit-remaining-tokens" to "Tokens remaining",
            "x-ratelimit-limit-tokens" to "Token limit",
            "x-ratelimit-reset-requests" to "Request reset",
            "x-ratelimit-reset-tokens" to "Token reset",
            "retry-after" to "Retry after"
        )
        val rateLimitSummary = headerNames.mapNotNull { (header, label) ->
            connection.getHeaderField(header)
                ?.takeIf { it.isNotBlank() }
                ?.let { "$label: $it" }
        }.joinToString(" · ").ifBlank { "Not provided by provider" }

        return ApiResult(
            result, promptTokens, completionTokens, totalTokens, rateLimitSummary
        )
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun ChatApp(activity: Activity) {
    val context = activity
    val preferences = remember { prefs(context) }

    val sessionState = remember {
        mutableStateListOf<ChatSession>().apply {
            addAll(loadSessions(context))
        }
    }
    var activeSessionId by remember {
        mutableStateOf(sessionState.firstOrNull()?.id.orEmpty())
    }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(sessionState.firstOrNull()?.messages.orEmpty())
        }
    }

    fun persistActiveSession() {
        val index = sessionState.indexOfFirst { it.id == activeSessionId }
        if (index >= 0) {
            val current = sessionState[index]
            sessionState[index] = current.copy(
                title = sessionTitle(messages),
                updatedAt = System.currentTimeMillis(),
                messages = messages.toList()
            )
            val ordered = sessionState.sortedByDescending { it.updatedAt }.take(50)
            sessionState.clear()
            sessionState.addAll(ordered)
            saveSessions(context, sessionState)
            saveHistory(context, messages)
        }
    }

    var draft by remember { mutableStateOf("") }
    var apiKey by remember {
        mutableStateOf(SecureKey.decrypt(preferences.getString("api_key", "").orEmpty()))
    }
    var baseUrl by remember {
        mutableStateOf(preferences.getString("base_url", "https://api.openai.com/v1").orEmpty())
    }
    var model by remember {
        mutableStateOf(preferences.getString("model", "gpt-4o-mini").orEmpty())
    }
    var language by remember {
        mutableStateOf(preferences.getString("language", "Auto").orEmpty())
    }
    var mode by remember {
        mutableStateOf(preferences.getString("mode", "Chat").orEmpty())
    }
    var systemPrompt by remember {
        mutableStateOf(preferences.getString(
            "system_prompt",
            "You are G.I KO KO CHAT, a helpful assistant. Reply in Burmese when the user writes Burmese."
        ).orEmpty())
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var imageStudioOpen by remember { mutableStateOf(false) }
    var imagePrompt by remember { mutableStateOf("") }
    var imageApiKey by remember {
        mutableStateOf(SecureKey.decrypt(preferences.getString("image_api_key", "").orEmpty()))
    }
    var imageBaseUrl by remember {
        mutableStateOf(preferences.getString("image_base_url", "https://api.openai.com/v1").orEmpty())
    }
    var imageModel by remember {
        mutableStateOf(preferences.getString("image_model", "gpt-image-1").orEmpty())
    }
    var imageLoading by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf("") }
    var currentImagePath by remember { mutableStateOf<String?>(null) }
    val generatedImages = remember {
        mutableStateListOf<GeneratedImage>().apply {
            addAll(loadGeneratedImages(context))
        }
    }
    var historyOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var retryHistory by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var pendingRetryPrompt by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var attachmentText by remember { mutableStateOf("") }
    var attachmentName by remember { mutableStateOf("") }

    var totalPromptTokens by remember { mutableStateOf(preferences.getInt("usage_prompt_total", 0)) }
    var totalCompletionTokens by remember { mutableStateOf(preferences.getInt("usage_completion_total", 0)) }
    var totalTokens by remember { mutableStateOf(preferences.getInt("usage_total", 0)) }
    var requestCount by remember { mutableStateOf(preferences.getInt("usage_request_count", 0)) }
    var lastPromptTokens by remember { mutableStateOf(preferences.getInt("usage_last_prompt", -1)) }
    var lastCompletionTokens by remember { mutableStateOf(preferences.getInt("usage_last_completion", -1)) }
    var lastTotalTokens by remember { mutableStateOf(preferences.getInt("usage_last_total", -1)) }
    var rateLimitSummary by remember {
        mutableStateOf(preferences.getString("usage_rate_limits", "Not provided by provider").orEmpty())
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = context.contentResolver.query(
                    uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else "Attached file"
                } ?: "Attached file"

                if (name.endsWith(".pdf", true) ||
                    name.endsWith(".png", true) ||
                    name.endsWith(".jpg", true) ||
                    name.endsWith(".jpeg", true)) {
                    attachmentName = name
                    attachmentText = ""
                    error = "ဒီ version မှာ PDF/Image content extraction မပါသေးပါ။ TXT, CSV, JSON, XML ဖိုင်ကို ရွေးပါ။"
                } else {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IOException("Cannot read selected file.")
                    attachmentName = name
                    attachmentText = text.take(20000)
                    error = if (text.length > 20000) "ဖိုင်စာသား၏ ပထမ 20,000 characters ကိုသာ အသုံးပြုပါမည်။" else ""
                }
            } catch (e: Exception) {
                error = "ဖိုင်ဖတ်မရပါ: ${e.message ?: "unknown error"}"
            }
        }
    }

    val saveImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        val path = currentImagePath
        if (uri != null && path != null) {
            try {
                val source = File(path)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("Cannot open destination file.")
                imageError = "ပုံကို ဖုန်းထဲ သိမ်းပြီးပါပြီ။"
            } catch (e: Exception) {
                imageError = "ပုံသိမ်းမရပါ: ${e.message ?: "unknown error"}"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val transcript = messages.joinToString("\n\n") { message ->
                    val speaker = if (message.role == "user") "You" else "G.I"
                    "$speaker:\n${message.content}"
                }
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Cannot open the selected save location.")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(transcript) }
                error = "Chat transcript saved successfully."
            } catch (e: Exception) {
                error = "Export failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun createNewSession() {
        if (loading) return
        retryHistory = null
        pendingRetryPrompt = null
        error = ""
        persistActiveSession()
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            updatedAt = System.currentTimeMillis(),
            messages = listOf(ChatMessage("assistant", WELCOME_MESSAGE))
        )
        sessionState.add(0, session)
        while (sessionState.size > 50) sessionState.removeAt(sessionState.lastIndex)
        activeSessionId = session.id
        messages.clear()
        messages.addAll(session.messages)
        draft = ""
        attachmentText = ""
        attachmentName = ""
        error = ""
        saveSessions(context, sessionState)
        saveHistory(context, messages)
    }

    fun openSession(session: ChatSession) {
        if (loading || session.id == activeSessionId) return
        retryHistory = null
        pendingRetryPrompt = null
        error = ""
        persistActiveSession()
        activeSessionId = session.id
        messages.clear()
        messages.addAll(session.messages)
        draft = ""
        attachmentText = ""
        attachmentName = ""
        error = ""
        saveHistory(context, messages)
    }

    fun deleteSession(session: ChatSession) {
        if (loading) return
        retryHistory = null
        pendingRetryPrompt = null
        error = ""
        sessionState.removeAll { it.id == session.id }
        if (session.id == activeSessionId) {
            val replacement = sessionState.firstOrNull()
            if (replacement != null) {
                activeSessionId = replacement.id
                messages.clear()
                messages.addAll(replacement.messages)
            } else {
                val fresh = ChatSession(
                    id = UUID.randomUUID().toString(),
                    title = "New chat",
                    updatedAt = System.currentTimeMillis(),
                    messages = listOf(ChatMessage("assistant", WELCOME_MESSAGE))
                )
                sessionState.add(fresh)
                activeSessionId = fresh.id
                messages.clear()
                messages.addAll(fresh.messages)
            }
            draft = ""
            attachmentText = ""
            attachmentName = ""
            error = ""
        }
        saveSessions(context, sessionState)
        saveHistory(context, messages)
    }


    val modes = listOf("Chat", "Translator", "Code Helper", "Summarizer", "Writing Assistant")
    val modePrompt = when (mode) {
        "Translator" -> "Translate accurately. Preserve meaning and formatting. If the target language is unclear, ask briefly."
        "Code Helper" -> "Act as a careful programming assistant. Explain assumptions and provide working code."
        "Summarizer" -> "Summarize the supplied content clearly, preserving key facts and important details."
        "Writing Assistant" -> "Help write and improve text according to the user's requested tone and purpose."
        else -> systemPrompt
    }
    val effectivePrompt = when (language) {
        "Burmese" -> "$modePrompt Always respond in Burmese unless the user explicitly requests another language."
        "English" -> "$modePrompt Always respond in English."
        else -> modePrompt
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Image(
            painter = painterResource(R.drawable.gi_landscape_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.30f
        )
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0x66100B12))
        ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xE6100B12)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.gi_3d_logo_concept),
                contentDescription = "G.I logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp)
            )
            Column(Modifier.weight(1f)) {
                Text("G.I KO KO CHAT", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$model · $mode", color = Muted, fontSize = 12.sp)
                Text("AI Chat Assistant", color = Muted, fontSize = 11.sp)
            }
            TextButton(
                onClick = {
                    exportLauncher.launch("GI-KO-KO-CHAT-${System.currentTimeMillis()}.txt")
                },
                enabled = !loading
            ) {
                Text("Export", color = Gold)
            }
            TextButton(onClick = { settingsOpen = true }) {
                Text("Settings", color = Gold)
            }
            TextButton(onClick = {
                imageError = ""
                imageStudioOpen = true
            }) {
                Text("Image", color = Gold)
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xFF19111B)).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { createNewSession() },
                enabled = !loading,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("+ New Chat", color = Gold, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { historyOpen = true },
                enabled = !loading,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("History (${sessionState.size})", color = Gold, fontSize = 12.sp)
            }
        }

        if (historyOpen) {
            AlertDialog(
                onDismissRequest = { historyOpen = false },
                title = { Text("Chat History", color = Gold) },
                text = {
                    if (sessionState.isEmpty()) {
                        Text("No saved chats yet.", color = White)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                sessionState,
                                key = { _, session -> session.id }
                            ) { _, session ->
                                Surface(
                                    color = if (session.id == activeSessionId) Color(0xFF1A3448) else Panel,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(
                                            (if (session.id == activeSessionId) "● " else "") + session.title,
                                            color = White,
                                            fontSize = 13.sp,
                                            maxLines = 2
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    openSession(session)
                                                    historyOpen = false
                                                }
                                            ) {
                                                Text("Open", color = Gold)
                                            }
                                            TextButton(
                                                onClick = { deleteSession(session) },
                                                enabled = !loading
                                            ) {
                                                Text("Delete", color = Color(0xFFFF8A80))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { historyOpen = false }) {
                        Text("Close", color = Gold)
                    }
                },
                containerColor = Panel,
                titleContentColor = Gold,
                textContentColor = White
            )
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
            modes.forEach { item ->
                TextButton(
                    onClick = { mode = item; preferences.edit().putString("mode", item).apply() },
                    enabled = !loading,
                    modifier = Modifier.padding(horizontal = 3.dp)
                ) {
                    Text(
                        item,
                        fontSize = 11.sp,
                        color = if (mode == item) Gold else Muted
                    )
                }
            }
        }

        if (attachmentName.isNotBlank()) {
            Text(
                "📎 $attachmentName",
                color = Muted,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            color = Panel,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold.copy(alpha = 0.85f))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("USAGE DASHBOARD", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    "Input: $totalPromptTokens · Output: $totalCompletionTokens · Total: $totalTokens tokens",
                    color = White, fontSize = 12.sp
                )
                Text(
                    if (lastTotalTokens >= 0)
                        "Last request: in $lastPromptTokens / out $lastCompletionTokens / total $lastTotalTokens tokens"
                    else "Last request: token usage unavailable",
                    color = Muted, fontSize = 11.sp
                )
                Text("Successful requests tracked: $requestCount", color = Muted, fontSize = 11.sp)
                Text("Rate limits: $rateLimitSummary", color = Muted, fontSize = 10.sp)
                Text("API credit balance: Not available from generic endpoint", color = Muted, fontSize = 10.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            itemsIndexed(messages) { _, message ->
                val user = message.role == "user"
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (user) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (user) Color(0xFF65491F) else Panel,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomEnd = if (user) 5.dp else 16.dp,
                            bottomStart = if (user) 16.dp else 5.dp
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (user) Gold.copy(alpha = 0.72f)
                            else Color(0xFF654A5A)
                        ),
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(0.94f)
                    ) {
                        SelectionContainer {
                            Text(
                                message.content,
                                color = White,
                                modifier = Modifier.padding(13.dp)
                            )
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Gold,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("G.I is thinking…", color = Muted)
                    }
                }
            }
        }

        fun retryLastRequest() {
            val historyToRetry = retryHistory ?: return
            if (loading) return

            if (apiKey.isBlank()) {
                error = "Settings ထဲမှာ OpenAI API key ထည့်ပါ။"
                settingsOpen = true
                return
            }
            if (model.isBlank() || baseUrl.isBlank()) {
                error = "Model ID နဲ့ API base URL ကို ဖြည့်ပါ။"
                settingsOpen = true
                return
            }

            val retryKey = apiKey
            val retryModel = model
            val retryBase = baseUrl
            val promptToRetry = pendingRetryPrompt ?: effectivePrompt

            loading = true
            error = ""

            Thread {
                try {
                    val result = requestOpenAI(
                        retryBase,
                        retryKey,
                        retryModel,
                        promptToRetry,
                        historyToRetry
                    )

                    activity.runOnUiThread {
                        messages.add(ChatMessage("assistant", result.content))
                        persistActiveSession()
                        requestCount += 1

                        result.promptTokens?.let {
                            totalPromptTokens += it
                            lastPromptTokens = it
                        }
                        result.completionTokens?.let {
                            totalCompletionTokens += it
                            lastCompletionTokens = it
                        }
                        result.totalTokens?.let {
                            totalTokens += it
                            lastTotalTokens = it
                        }
                        rateLimitSummary = result.rateLimitSummary
                        retryHistory = null
                        pendingRetryPrompt = null

                        preferences.edit()
                            .putInt("usage_prompt_total", totalPromptTokens)
                            .putInt("usage_completion_total", totalCompletionTokens)
                            .putInt("usage_total", totalTokens)
                            .putInt("usage_request_count", requestCount)
                            .putInt("usage_last_prompt", lastPromptTokens)
                            .putInt("usage_last_completion", lastCompletionTokens)
                            .putInt("usage_last_total", lastTotalTokens)
                            .putString("usage_rate_limits", rateLimitSummary)
                            .apply()

                        loading = false
                    }
                } catch (e: Exception) {
                    activity.runOnUiThread {
                        error = e.message ?: "Network request failed."
                        loading = false
                    }
                }
            }.start()
        }

        if (error.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    error,
                    color = Color(0xFFFF9C8D),
                    fontSize = 12.sp
                )
                if (retryHistory != null && !loading) {
                    Row {
                        TextButton(onClick = { retryLastRequest() }) {
                            Text("Retry  ↻", color = Gold, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = {
                            retryHistory = null
                            pendingRetryPrompt = null
                            error = ""
                        }) {
                            Text("Dismiss", color = Muted)
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF19111B),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp, Gold.copy(alpha = 0.85f)
            ),
            shadowElevation = 5.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            TextButton(
                onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/xml")) },
                enabled = !loading
            ) { Text("+", color = Gold, fontSize = 22.sp) }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                placeholder = {
                    Text("Message G.I…", color = Muted)
                },
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF291D2B),
                    unfocusedContainerColor = Color(0xFF291D2B),
                    disabledContainerColor = Color(0xFF291D2B),
                    focusedIndicatorColor = Gold,
                    unfocusedIndicatorColor = Color(0xFF5B4936),
                    disabledIndicatorColor = Color(0xFF5B4936),
                    cursorColor = Gold,
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    focusedPlaceholderColor = Muted,
                    unfocusedPlaceholderColor = Muted
                ),
                enabled = !loading,
                maxLines = 5
            )

            TextButton(
                enabled = !loading && retryHistory == null && (draft.isNotBlank() || attachmentText.isNotBlank()),
                onClick = {
                    if (apiKey.isBlank()) {
                        error = "Settings ထဲမှာ OpenAI API key ထည့်ပါ။"
                        settingsOpen = true
                        return@TextButton
                    }
                    if (model.isBlank() || baseUrl.isBlank()) {
                        error = "Model ID နဲ့ API base URL ကို ဖြည့်ပါ။"
                        settingsOpen = true
                        return@TextButton
                    }

                    val userText = buildString {
                        append(draft.trim())
                        if (attachmentText.isNotBlank()) {
                            append("\n\n[File: $attachmentName]\n")
                            append(attachmentText)
                        }
                    }
                    messages.add(ChatMessage("user", userText))
                    persistActiveSession()
                    draft = ""
                    attachmentText = ""
                    attachmentName = ""
                    error = ""
                    loading = true

                    val requestHistory = messages.toList()
                    retryHistory = requestHistory
                    val requestKey = apiKey
                    val requestModel = model
                    val requestBase = baseUrl
                    val requestPrompt = effectivePrompt
                    pendingRetryPrompt = requestPrompt

                    Thread {
                        try {
                            val result = requestOpenAI(
                                requestBase, requestKey, requestModel,
                                requestPrompt, requestHistory
                            )
                            activity.runOnUiThread {
                                messages.add(ChatMessage("assistant", result.content))
                                persistActiveSession()

                                requestCount += 1
                                result.promptTokens?.let {
                                    totalPromptTokens += it
                                    lastPromptTokens = it
                                }
                                result.completionTokens?.let {
                                    totalCompletionTokens += it
                                    lastCompletionTokens = it
                                }
                                result.totalTokens?.let {
                                    totalTokens += it
                                    lastTotalTokens = it
                                }
                                rateLimitSummary = result.rateLimitSummary
                                retryHistory = null
                                pendingRetryPrompt = null

                                preferences.edit()
                                    .putInt("usage_prompt_total", totalPromptTokens)
                                    .putInt("usage_completion_total", totalCompletionTokens)
                                    .putInt("usage_total", totalTokens)
                                    .putInt("usage_request_count", requestCount)
                                    .putInt("usage_last_prompt", lastPromptTokens)
                                    .putInt("usage_last_completion", lastCompletionTokens)
                                    .putInt("usage_last_total", lastTotalTokens)
                                    .putString("usage_rate_limits", rateLimitSummary)
                                    .apply()
                                loading = false
                            }
                        } catch (e: Exception) {
                            activity.runOnUiThread {
                                error = e.message ?: "Network request failed."
                                loading = false
                            }
                        }
                    }.start()
                }
            ) {
                Text("Send  ➤", color = Gold, fontWeight = FontWeight.Bold)
            }
            }
        }

        Text(
            "API usage may incur charges. Never share your API key.",
            color = Muted, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 5.dp)
        )
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("API & Chat Settings", color = Gold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("OpenAI API key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model ID (e.g. gpt-4o-mini)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("System prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    Text("Response language", color = Gold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Auto", "Burmese", "English").forEach { option ->
                            FilterChip(
                                selected = language == option,
                                onClick = { language = option },
                                label = { Text(option) }
                            )
                        }
                    }
                    Text(
                        "API key ကို Android Keystore AES-GCM နဲ့ encrypt လုပ်သိမ်းပါတယ်။ " +
                        "ဖုန်း app ထဲမှာ API key ထည့်သုံးတာက public distribution အတွက် လုံးဝလုံခြုံတယ်လို့ မဆိုလိုပါ။",
                        color = Muted, fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        preferences.edit()
                            .putString("api_key", SecureKey.encrypt(apiKey.trim()))
                            .putString("base_url", baseUrl.trim())
                            .putString("model", model.trim())
                            .putString("language", language)
                            .putString("mode", mode)
                            .putString("system_prompt", systemPrompt)
                            .apply()
                        error = ""
                        settingsOpen = false
                    } catch (e: Exception) {
                        error = "Settings save failed: ${e.message}"
                    }
                }) { Text("Save", color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = { settingsOpen = false }) {
                    Text("Cancel", color = Muted)
                }
            },
            containerColor = Panel,
            titleContentColor = Gold,
            textContentColor = White
        )
    }

    if (imageStudioOpen) {
        val previewPath = currentImagePath
        val previewBitmap = remember(previewPath) {
            previewPath?.let { path ->
                try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!imageLoading) imageStudioOpen = false
            },
            title = { Text("G.I IMAGE STUDIO", color = Gold) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = imagePrompt,
                        onValueChange = { imagePrompt = it },
                        label = { Text("Describe the image / ပုံဖော်ပြချက်") },
                        placeholder = { Text("A golden Burmese fantasy palace at sunset…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        enabled = !imageLoading
                    )
                    OutlinedTextField(
                        value = imageApiKey,
                        onValueChange = { imageApiKey = it },
                        label = { Text("Image API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !imageLoading
                    )
                    OutlinedTextField(
                        value = imageBaseUrl,
                        onValueChange = { imageBaseUrl = it },
                        label = { Text("Image API Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !imageLoading
                    )
                    OutlinedTextField(
                        value = imageModel,
                        onValueChange = { imageModel = it },
                        label = { Text("Image Model ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !imageLoading
                    )
                    Text(
                        "API သည် /images/generations endpoint နှင့် " +
                            "data[0].url သို့မဟုတ် data[0].b64_json ကို ပံ့ပိုးရပါမည်။ " +
                            "အသုံးပြုခ ရှိနိုင်သည်။",
                        color = Muted,
                        fontSize = 11.sp
                    )

                    if (imageLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Gold,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("ပုံဖန်တီးနေပါတယ်…", color = White)
                        }
                    }

                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Generated image preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        )
                        TextButton(
                            onClick = {
                                saveImageLauncher.launch(
                                    "GI-Image-${System.currentTimeMillis()}.png"
                                )
                            },
                            enabled = !imageLoading
                        ) {
                            Text("↓ Save PNG to phone", color = Gold)
                        }
                    }

                    if (generatedImages.isNotEmpty()) {
                        Text("Recent images", color = Gold, fontWeight = FontWeight.Bold)
                        Column(
                            Modifier.heightIn(max = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            generatedImages.take(8).forEach { item ->
                                TextButton(
                                    onClick = {
                                        currentImagePath = item.path
                                        imagePrompt = item.prompt
                                        imageError = ""
                                    },
                                    enabled = !imageLoading
                                ) {
                                    Text(
                                        "🖼 ${item.prompt.ifBlank { "Saved image" }.take(55)}",
                                        color = White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (imageError.isNotBlank()) {
                        Text(imageError, color = Color(0xFFFF9C8D), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !imageLoading &&
                        imagePrompt.isNotBlank() &&
                        imageApiKey.isNotBlank() &&
                        imageBaseUrl.isNotBlank() &&
                        imageModel.isNotBlank(),
                    onClick = {
                        val prompt = imagePrompt.trim()
                        val key = imageApiKey.trim()
                        val base = imageBaseUrl.trim()
                        val modelName = imageModel.trim()

                        try {
                            preferences.edit()
                                .putString("image_api_key", SecureKey.encrypt(key))
                                .putString("image_base_url", base)
                                .putString("image_model", modelName)
                                .apply()
                        } catch (e: Exception) {
                            imageError = "Image settings save failed: ${e.message}"
                            return@TextButton
                        }

                        imageLoading = true
                        imageError = ""
                        Thread {
                            try {
                                val bytes = generateImageOpenAI(
                                    base, key, modelName, prompt
                                )
                                val dir = File(context.filesDir, "gi_images")
                                if (!dir.exists() && !dir.mkdirs()) {
                                    throw IOException("Cannot create image storage directory.")
                                }
                                val stamp = System.currentTimeMillis()
                                val imageFile = File(dir, "gi_image_$stamp.png")
                                imageFile.writeBytes(bytes)
                                File(dir, "gi_image_$stamp.txt")
                                    .writeText(prompt, Charsets.UTF_8)

                                activity.runOnUiThread {
                                    generatedImages.add(
                                        0,
                                        GeneratedImage(
                                            imageFile.absolutePath,
                                            prompt,
                                            stamp
                                        )
                                    )
                                    while (generatedImages.size > 30) {
                                        generatedImages.removeAt(generatedImages.lastIndex)
                                    }
                                    currentImagePath = imageFile.absolutePath
                                    imageLoading = false
                                    imageError = "ပုံဖန်တီးပြီးပါပြီ။ Save PNG ကိုနှိပ်ပြီး သိမ်းနိုင်ပါတယ်။"
                                }
                            } catch (e: Exception) {
                                activity.runOnUiThread {
                                    imageLoading = false
                                    imageError = e.message ?: "Image generation failed."
                                }
                            }
                        }.start()
                    }
                ) {
                    Text("Generate Image ✨", color = Gold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { imageStudioOpen = false },
                    enabled = !imageLoading
                ) {
                    Text("Close", color = Muted)
                }
            },
            containerColor = Panel,
            titleContentColor = Gold,
            textContentColor = White
        )
    }

}
    }
