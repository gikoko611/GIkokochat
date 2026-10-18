package com.gi.kokochat

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Bg = Color(0xFF07111E)
private val Panel = Color(0xFF102236)
private val Gold = Color(0xFFFFC43D)
private val White = Color(0xFFEDF5FF)
private val Muted = Color(0xFF91A8BC)

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

private fun saveHistory(context: Context, messages: List<ChatMessage>) {
    val array = JSONArray()
    messages.takeLast(200).forEach {
        array.put(JSONObject().put("role", it.role).put("content", it.content))
    }
    prefs(context).edit().putString("history", array.toString()).apply()
}

private fun requestOpenAI(
    base: String,
    apiKey: String,
    model: String,
    systemPrompt: String,
    history: List<ChatMessage>
): String {
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
        return result
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun ChatApp(activity: Activity) {
    val context = activity
    val preferences = remember { prefs(context) }

    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(loadHistory(context))
            if (isEmpty()) add(ChatMessage(
                "assistant",
                "မင်္ဂလာပါ။ G.I KO KO CHAT မှ ကြိုဆိုပါတယ်။ Settings ထဲမှာ OpenAI API key ထည့်ပြီး စတင်အသုံးပြုနိုင်ပါတယ်။"
            ))
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
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var attachmentText by remember { mutableStateOf("") }
    var attachmentName by remember { mutableStateOf("") }

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

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF091522)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("G.I KO KO CHAT", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$model · $mode", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = { settingsOpen = true }) {
                Text("Settings", color = Gold)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
            modes.forEach { item ->
                TextButton(
                    onClick = { mode = item; preferences.edit().putString("mode", item).apply() },
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        item,
                        fontSize = 9.sp,
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
                        color = if (user) Color(0xFF1456A7) else Panel,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.94f)
                    ) {
                        Text(
                            message.content,
                            color = White,
                            modifier = Modifier.padding(13.dp)
                        )
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

        if (error.isNotBlank()) {
            Text(
                error,
                color = Color(0xFFFF9C8D),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(10.dp)
                .background(Panel, RoundedCornerShape(18.dp)).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/xml")) },
                enabled = !loading
            ) { Text("+", color = Gold, fontSize = 22.sp) }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message G.I…", color = Muted) },
                enabled = !loading,
                maxLines = 5
            )

            TextButton(
                enabled = !loading && (draft.isNotBlank() || attachmentText.isNotBlank()),
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
                    saveHistory(context, messages)
                    draft = ""
                    attachmentText = ""
                    attachmentName = ""
                    error = ""
                    loading = true

                    val requestHistory = messages.toList()
                    val requestKey = apiKey
                    val requestModel = model
                    val requestBase = baseUrl
                    val requestPrompt = effectivePrompt

                    Thread {
                        try {
                            val answer = requestOpenAI(
                                requestBase, requestKey, requestModel,
                                requestPrompt, requestHistory
                            )
                            activity.runOnUiThread {
                                messages.add(ChatMessage("assistant", answer))
                                saveHistory(context, messages)
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
            ) { Text("Send", color = Gold, fontWeight = FontWeight.Bold) }
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
}
