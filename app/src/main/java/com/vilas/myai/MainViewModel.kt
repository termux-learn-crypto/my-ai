package com.vilas.myai

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vilas.myai.stt.SpeechCallbacks
import com.vilas.myai.stt.SpeechInput
import com.vilas.myai.stt.SttErrorKind
import com.vilas.myai.tts.VoiceOut
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ChatMsg(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val meta: String = "",
    val langTag: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app), SpeechCallbacks {
    private val prefs = app.getSharedPreferences("myai", Context.MODE_PRIVATE)
    private val stt = SpeechInput(app, this)
    private val voice = VoiceOut(app)
    private var speakJob: Job? = null
    private var idSeq = 1L

    val messages = mutableStateListOf<ChatMsg>()
    var mode by mutableStateOf(LangMode.entries.getOrElse(prefs.getInt("mode", 0)) { LangMode.AUTO })
        private set
    var female by mutableStateOf(prefs.getBoolean("female", true))
        private set
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1.0f))
        private set
    var neural by mutableStateOf(prefs.getBoolean("neural", true))
        private set
    var listening by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
    var errorKind by mutableStateOf<SttErrorKind?>(null)
    private var lastAutoTag = prefs.getString("lastAutoTag", "hi-IN") ?: "hi-IN"

    init {
        messages.add(ChatMsg(idSeq++, false,
            "Namaste Vilas! Mic dabao aur bolo (Hindi, Hinglish, Marathi ya English). Main wahi likh ke bol ke sunaunga.",
            "My AI"))
    }

    fun selectMode(m: LangMode) { mode = m; prefs.edit().putInt("mode", m.ordinal).apply() }
    fun setFemaleVoice(f: Boolean) { female = f; prefs.edit().putBoolean("female", f).apply() }
    fun updateSpeed(s: Float) { speed = s; prefs.edit().putFloat("speed", s).apply() }
    fun setNeuralVoice(n: Boolean) { neural = n; prefs.edit().putBoolean("neural", n).apply() }

    fun speechAvailable() = stt.isAvailable()

    fun toggleMic() {
        if (listening) { stt.stopListening(); return }
        voice.stop(); speakJob?.cancel(); speaking = false
        error = null; errorKind = null
        if (!stt.isAvailable()) {
            errorKind = SttErrorKind.SERVICE_MISSING
            error = "Phone me Google speech service nahi mila. 'Speech Recognition & Synthesis' / Google app install ya update karo."
            return
        }
        partial = ""
        listening = true
        stt.start(mode, lastAutoTag)
    }

    fun sendTyped(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        error = null; errorKind = null
        handleUserText(t, null, typed = true)
    }

    fun replay(msg: ChatMsg) = speakOut(msg.text, msg.langTag)

    private fun handleUserText(text: String, detected: String?, typed: Boolean) {
        val langLabel = detected?.let { " · $it" } ?: ""
        messages.add(ChatMsg(idSeq++, true, text, (if (typed) "typed" else "voice") + langLabel, detected))
        speakOut(text, detected, addEcho = true)
    }

    private fun speakOut(text: String, detected: String?, addEcho: Boolean = false) {
        voice.stop(); speakJob?.cancel()
        speakJob = viewModelScope.launch {
            speaking = true
            if (neural && !isOnline()) {
                error = "Internet nahi hai: neural awaaz ki jagah phone ki awaaz use hogi."
            }
            val r = voice.speak(text, mode, detected, female, speed, neural && isOnline())
            if (addEcho) {
                val meta = "${r.engine} · ${r.voice}"
                messages.add(ChatMsg(idSeq++, false, text, meta, detected))
            }
            r.note?.let { error = it }
            speaking = false
        }
    }

    fun stopSpeaking() { voice.stop(); speakJob?.cancel(); speaking = false }

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // --- SpeechCallbacks (called on main thread) ---
    override fun onReady() { listening = true }
    override fun onPartial(text: String) { partial = text }
    override fun onLevel(rmsDb: Float) { level = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f) }
    override fun onFinal(text: String, detectedLang: String?) {
        partial = ""
        if (mode == LangMode.AUTO && detectedLang != null) {
            lastAutoTag = detectedLang
            prefs.edit().putString("lastAutoTag", detectedLang).apply()
        }
        handleUserText(text, detectedLang, typed = false)
    }
    override fun onError(kind: SttErrorKind, message: String) { partial = ""; errorKind = kind; error = message }
    override fun onEnd() { listening = false; level = 0f }

    override fun onCleared() {
        stt.stop(); voice.shutdown()
        super.onCleared()
    }
}
