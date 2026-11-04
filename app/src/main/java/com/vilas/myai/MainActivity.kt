package com.vilas.myai

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vilas.myai.stt.SttErrorKind

private val Bg = Color(0xFF0E1116)
private val Surface1 = Color(0xFF171B22)
private val Surface2 = Color(0xFF222833)
private val Accent = Color(0xFF6C4DFF)
private val UserBubble = Color(0xFF3B2F8F)
private val TextMain = Color(0xFFECEFF4)
private val TextDim = Color(0xFF8B93A1)
private val Danger = Color(0xFFFF5C6C)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface1)) {
                AppScreen(vm)
            }
        }
    }
}

fun openPlayStore(ctx: android.content.Context, pkg: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openAppSettings(ctx: android.content.Context) {
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var micDenied by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { micDenied = false; vm.toggleMic() } else {
            micDenied = true
            vm.errorKind = SttErrorKind.MIC_DENIED
            vm.error = "Mic permission chahiye. Allow karo, warna bol ke kaam nahi hoga."
        }
    }

    fun onMic() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.toggleMic()
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val itemCount = vm.messages.size + if (vm.partial.isNotBlank()) 1 else 0
    LaunchedEffect(itemCount, vm.partial) { if (itemCount > 0) listState.animateScrollToItem(itemCount - 1) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My AI", color = TextMain, fontWeight = FontWeight.Bold)
                        Text(
                            when { vm.listening -> "Sun raha hoon…"; vm.speaking -> "Bol raha hoon…"; else -> "Voice echo · Step 1" },
                            color = TextDim, fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Settings", tint = TextMain) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            // Language chips
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LangMode.entries.forEach { m ->
                    val sel = vm.mode == m
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) Accent else Surface2)
                            .clickable { vm.selectMode(m) }.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text(m.label, color = if (sel) Color.White else TextDim, fontSize = 13.sp) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(vm.messages, key = { it.id }) { m -> Bubble(m, onReplay = { vm.replay(m) }) }
                if (vm.partial.isNotBlank()) {
                    item(key = "partial") { Bubble(ChatMsg(-1, true, vm.partial, "sun raha hoon…"), onReplay = null, live = true) }
                }
            }

            vm.error?.let { err ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FF5C6C)).padding(10.dp)) {
                    Text(err, color = TextMain, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (vm.errorKind) {
                            SttErrorKind.SERVICE_MISSING, SttErrorKind.LANG_MISSING -> {
                                TextButton(onClick = { openPlayStore(ctx, "com.google.android.tts") }) { Text("Speech Services kholo") }
                                TextButton(onClick = { openPlayStore(ctx, "com.google.android.googlequicksearchbox") }) { Text("Google app") }
                            }
                            SttErrorKind.MIC_DENIED -> if (micDenied) TextButton(onClick = { openAppSettings(ctx) }) { Text("Settings me Allow karo") }
                            else -> {}
                        }
                        TextButton(onClick = { vm.error = null; vm.errorKind = null }) { Text("Theek hai") }
                    }
                }
            }

            // Input row
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type karo…", color = TextDim) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                        focusedBorderColor = Accent, unfocusedBorderColor = Surface2,
                        focusedContainerColor = Surface1, unfocusedContainerColor = Surface1
                    )
                )
                Spacer(Modifier.width(8.dp))
                if (input.isNotBlank()) {
                    FilledIconButton(onClick = { vm.sendTyped(input); input = "" }, modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent)) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                    }
                } else {
                    MicButton(vm.listening, vm.level, vm.speaking, onClick = { if (vm.speaking) vm.stopSpeaking() else onMic() })
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = Surface1) {
            Column(Modifier.padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Awaaz settings", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.female, onClick = { vm.setFemaleVoice(true) }, label = { Text("Female (Swara/Aarohi/Neerja)") })
                    FilterChip(selected = !vm.female, onClick = { vm.setFemaleVoice(false) }, label = { Text("Male") })
                }
                Text("Speed: ${"%.2f".format(vm.speed)}x", color = TextMain)
                Slider(value = vm.speed, onValueChange = { vm.updateSpeed(it) }, valueRange = 0.6f..1.8f, steps = 11)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Neural awaaz (best, internet chahiye)", color = TextMain)
                        Text("Off = phone ki Google TTS awaaz (offline)", color = TextDim, fontSize = 12.sp)
                    }
                    Switch(checked = vm.neural, onCheckedChange = { vm.setNeuralVoice(it) })
                }
                Text("Auto: Android 14+ pe Hindi/Marathi/English khud pehchaanta hai; purane phone pe last language use hoti hai. Hinglish = Hindi recognizer + Hindi awaaz.",
                    color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MicButton(listening: Boolean, level: Float, speaking: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (listening) 1f + level * 0.25f else 1f, label = "mic")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
        if (listening) Box(Modifier.size(72.dp).scale(scale).clip(CircleShape).background(Accent.copy(alpha = 0.35f)))
        Box(
            Modifier.size(60.dp).clip(CircleShape).background(if (listening) Danger else Accent).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (listening || speaking) Icons.Filled.Stop else Icons.Filled.Mic, "Mic", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
fun Bubble(m: ChatMsg, onReplay: (() -> Unit)?, live: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (m.fromUser) 18.dp else 4.dp, bottomEnd = if (m.fromUser) 4.dp else 18.dp))
                .background(if (m.fromUser) (if (live) UserBubble.copy(alpha = 0.5f) else UserBubble) else Surface2)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(m.text, color = TextMain, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.meta.isNotBlank()) Text(m.meta, color = TextDim, fontSize = 11.sp)
                if (!m.fromUser && onReplay != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, "Replay", tint = TextDim,
                        modifier = Modifier.size(18.dp).clickable { onReplay() })
                }
            }
        }
    }
}
