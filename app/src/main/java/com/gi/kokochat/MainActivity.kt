package com.gi.kokochat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bg=Color(0xFF07111E); private val panel=Color(0xFF102236); private val gold=Color(0xFFFFC43D); private val white=Color(0xFFEDF5FF); private val muted=Color(0xFF91A8BC)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{App()}}}
data class Msg(val user:Boolean,val text:String)
@Composable fun App(){
 var page by remember{mutableStateOf("Chat")}; var draft by remember{mutableStateOf("")}; var showAttach by remember{mutableStateOf(false)}
 var provider by remember{mutableStateOf("Demo mode")}; var providerName by remember{mutableStateOf("")}; var base by remember{mutableStateOf("https://api.openai.com/v1")}; var key by remember{mutableStateOf("")}; var model by remember{mutableStateOf("")}; var prompt by remember{mutableStateOf("You are G.I KO KO CHAT, a helpful assistant.")}
 val msgs= remember { mutableStateListOf(Msg(false,"မင်္ဂလာပါ။ G.I KO KO CHAT မှ ကြိုဆိုပါတယ်။\n\n＋ ကိုနှိပ်ပြီး PDF၊ ပုံနဲ့ အခြားဖိုင်တွေ ရွေးနိုင်ပါတယ်။")) }; val picked=remember{mutableStateListOf<String>()}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->uris.forEach{picked.add(it.lastPathSegment?:it.toString())};showAttach=false}
 MaterialTheme(colorScheme=darkColorScheme(primary=gold,background=bg,surface=panel,onSurface=white)){
 Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF15334A),bg,Color(0xFF030811))))){
 Row(Modifier.fillMaxWidth().background(bg).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
 Box(Modifier.size(43.dp).background(Brush.linearGradient(listOf(Color(0xFFFFE98B),gold,Color(0xFF653700))),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Text("GI",color=Color.Black,fontWeight=FontWeight.Black,fontSize=21.sp)}
 Column(Modifier.weight(1f).padding(start=11.dp)){Text("G.I KO KO CHAT",color=white,fontWeight=FontWeight.Bold,letterSpacing=1.sp);Text("Your AI · Your Way",color=muted,fontSize=12.sp)}
 IconButton(onClick={page="Settings"}){Icon(Icons.Default.Settings,"Settings",tint=gold)}
 }
  when (page) {
    "Settings", "API Keys" -> Column(Modifier.weight(1f).padding(16.dp)) {
      Text("API Keys & Settings", color = gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(providerName, { providerName = it }, label = { Text("Provider name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
      OutlinedTextField(base, { base = it }, label = { Text("API base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
      OutlinedTextField(key, { key = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
      OutlinedTextField(model, { model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
      OutlinedTextField(prompt, { prompt = it }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
      Button(onClick = {
        provider = if (key.isBlank()) "Demo mode" else providerName.ifBlank { "Custom provider" }
        page = "Chat"
      }, modifier = Modifier.fillMaxWidth()) { Text("Save configuration") }
      Text("Starter only: settings are not persisted and live API networking is not implemented yet.", color = muted, fontSize = 12.sp)
    }

    "Files" -> Column(Modifier.weight(1f).padding(16.dp)) {
      Text("▤ Files", color = gold, fontSize = 23.sp)
      Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("＋ Add files") }
      picked.forEach { Text("📎 $it", color = white, modifier = Modifier.padding(6.dp)) }
    }

    "Pro" -> Column(Modifier.weight(1f).padding(18.dp)) {
      Text("♛ G.I PRO", color = gold, fontSize = 26.sp, fontWeight = FontWeight.Black)
      listOf("Multiple AI providers", "PDF text extraction", "Image understanding", "Voice input/output", "Encrypted key storage", "Custom system prompts").forEach {
        Text("✓  $it", color = white, modifier = Modifier.padding(10.dp))
      }
      Text("Feature roadmap only; no billing implemented.", color = muted, fontSize = 12.sp)
    }

    else -> Column(Modifier.weight(1f).fillMaxWidth()) {
      Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("GI", color = gold, fontSize = 55.sp, fontWeight = FontWeight.Black)
        Text("G.I KO KO CHAT", color = gold, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Your AI · Your Way", color = muted, fontSize = 12.sp)
      }

      Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("MODEL", color = muted, fontSize = 11.sp)
        Text(provider, color = white, modifier = Modifier.padding(12.dp).weight(1f))
        IconButton(onClick = { page = "API Keys" }) { Icon(Icons.Default.Key, "API keys", tint = gold) }
      }

      LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items(msgs) { m ->
          Surface(
            color = if (m.user) Color(0xFF1456A7) else panel,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.94f).align(if (m.user) Alignment.End else Alignment.Start)
          ) {
            Text(m.text, color = white, modifier = Modifier.padding(13.dp))
          }
        }
        if (picked.isNotEmpty()) {
          item { picked.forEach { Text("📎 $it", color = muted) } }
        }
      }

      if (showAttach) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = panel)) {
          Column(Modifier.padding(10.dp)) {
            Text("Attach File / Media", color = gold)
            Row {
              listOf("Images" to "image/*", "PDF" to "application/pdf", "Documents" to "application/*").forEach { (label, mime) ->
                TextButton(onClick = { picker.launch(arrayOf(mime, "*/*")); showAttach = false }, modifier = Modifier.weight(1f)) {
                  Text(label, fontSize = 11.sp)
                }
              }
            }
            TextButton(onClick = { picker.launch(arrayOf("*/*")); showAttach = false }) { Text("Any file") }
          }
        }
      }

      Row(Modifier.fillMaxWidth().padding(10.dp).background(panel, RoundedCornerShape(18.dp)).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { showAttach = !showAttach }) { Icon(Icons.Default.Add, "Attach", tint = gold) }
        Box(Modifier.weight(1f).padding(10.dp)) {
          if (draft.isEmpty()) Text("Message G.I...", color = muted)
          BasicTextField(value = draft, onValueChange = { draft = it }, textStyle = androidx.compose.ui.text.TextStyle(color = white, fontSize = 15.sp), modifier = Modifier.fillMaxWidth())
        }
        IconButton(onClick = {
          if (draft.isNotBlank() || picked.isNotEmpty()) {
            msgs.add(Msg(true, draft.ifBlank { "Please help with attached file(s): " + picked.joinToString() }))
            msgs.add(Msg(false, "Starter APK UI အလုပ်လုပ်ပါတယ်။ Live API chat နဲ့ PDF/Image content analysis ကို နောက်တစ်ဆင့်မှာ ချိတ်ဆက်ရပါမယ်။"))
            draft = ""
            picked.clear()
          }
        }) { Icon(Icons.Default.Send, "Send", tint = gold) }
      }

      Text("Starter build · file names only, no content analysis yet", color = muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 5.dp))
    }
  }

  NavigationBar(containerColor = bg) {
    listOf("Chat", "Files", "API Keys", "Settings", "Pro").forEach { item ->
      NavigationBarItem(
        selected = page == item,
        onClick = { page = item },
        icon = {
          Icon(
            when (item) {
              "Chat" -> Icons.Default.Chat
              "Files" -> Icons.Default.Description
              "API Keys" -> Icons.Default.Key
              "Settings" -> Icons.Default.Settings
              else -> Icons.Default.Star
            },
            item
          )
        },
        label = { Text(item, fontSize = 9.sp) }
      )
    }
  }
 }
 }
}
