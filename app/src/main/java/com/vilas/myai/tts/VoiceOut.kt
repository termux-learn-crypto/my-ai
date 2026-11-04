package com.vilas.myai.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vilas.myai.LangMode
import com.vilas.myai.Script
import com.vilas.myai.SpeakLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

data class SpeakResult(val engine: String, val voice: String, val spokenText: String, val note: String? = null)

/**
 * Best quality: Microsoft Edge neural voices (online, free).
 * Fallback: Android TextToSpeech (Google engine) when offline / Edge fails.
 */
class VoiceOut(private val context: Context) {
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context.applicationContext, { status -> ttsReady = status == TextToSpeech.SUCCESS },
            "com.google.android.tts")
    }

    fun edgeVoice(lang: SpeakLang, female: Boolean): String = when (lang) {
        SpeakLang.HINDI -> if (female) "hi-IN-SwaraNeural" else "hi-IN-MadhurNeural"
        SpeakLang.MARATHI -> if (female) "mr-IN-AarohiNeural" else "mr-IN-ManoharNeural"
        SpeakLang.ENGLISH -> if (female) "en-IN-NeerjaNeural" else "en-IN-PrabhatNeural"
    }

    /** Decide spoken language + final text (with transliteration when useful). Runs on IO. */
    fun resolve(text: String, mode: LangMode, detectedTag: String?): Pair<SpeakLang, String> {
        val dev = Script.hasDevanagari(text)
        val roman = Script.devanagariRatio(text) < 0.2f && Script.hasLatin(text)
        val effective: LangMode = when (mode) {
            LangMode.AUTO -> when {
                detectedTag?.startsWith("mr") == true -> LangMode.MARATHI
                detectedTag?.startsWith("en") == true && roman && !Script.looksHinglish(text) -> LangMode.ENGLISH
                dev && Script.looksMarathi(text) -> LangMode.MARATHI
                dev -> LangMode.HINDI
                roman && Script.looksHinglish(text) -> LangMode.HINGLISH
                roman -> LangMode.ENGLISH
                else -> LangMode.HINDI
            }
            else -> mode
        }
        return when (effective) {
            LangMode.ENGLISH -> SpeakLang.ENGLISH to text
            LangMode.MARATHI -> SpeakLang.MARATHI to
                (if (roman) Transliterator.toDevanagari(text, "mr") ?: text else text)
            LangMode.HINDI, LangMode.HINGLISH, LangMode.AUTO -> {
                if (roman) {
                    val d = Transliterator.toDevanagari(text, "hi")
                    if (d != null) SpeakLang.HINDI to d else SpeakLang.ENGLISH to text
                } else SpeakLang.HINDI to text
            }
        }
    }

    /** speed: 0.5 .. 2.0 */
    suspend fun speak(
        text: String, mode: LangMode, detectedTag: String?, female: Boolean, speed: Float, preferNeural: Boolean
    ): SpeakResult {
        stop()
        val (lang, spoken) = withContext(Dispatchers.IO) { resolve(text, mode, detectedTag) }
        var note: String? = null
        if (preferNeural) {
            val voice = edgeVoice(lang, female)
            try {
                val mp3 = withContext(Dispatchers.IO) {
                    val rate = ((speed - 1f) * 100).toInt()
                    val parts = chunk(spoken, 900)
                    val out = java.io.ByteArrayOutputStream()
                    for (p in parts) out.write(EdgeTtsClient.synthesize(p, voice, rate))
                    out.toByteArray()
                }
                playMp3(mp3)
                return SpeakResult("Neural", voice, spoken)
            } catch (e: Exception) {
                note = "Neural voice nahi mili (${e.message?.take(60)}), phone ki awaaz use ki."
            }
        }
        val locale = when (lang) {
            SpeakLang.HINDI -> Locale("hi", "IN")
            SpeakLang.MARATHI -> Locale("mr", "IN")
            SpeakLang.ENGLISH -> Locale("en", "IN")
        }
        val ok = speakAndroid(spoken, locale, speed)
        if (!ok) note = (note ?: "") + " Phone TTS bhi fail: Google 'Speech Recognition & Synthesis' install/update karo."
        return SpeakResult("Phone", locale.toLanguageTag(), spoken, note?.trim())
    }

    private fun chunk(s: String, max: Int): List<String> {
        if (s.length <= max) return listOf(s)
        val out = ArrayList<String>()
        var rest = s
        while (rest.length > max) {
            var cut = rest.lastIndexOfAny(charArrayOf('।', '.', '!', '?', ','), max)
            if (cut < max / 3) cut = rest.lastIndexOf(' ', max)
            if (cut <= 0) cut = max
            out.add(rest.substring(0, cut + 1).trim())
            rest = rest.substring(cut + 1)
        }
        if (rest.isNotBlank()) out.add(rest.trim())
        return out.filter { it.isNotBlank() }
    }

    private suspend fun playMp3(bytes: ByteArray) {
        val f = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
        withContext(Dispatchers.IO) {
            context.cacheDir.listFiles { x -> x.name.startsWith("tts_") }?.forEach { it.delete() }
            f.writeBytes(bytes)
        }
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            player = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                mp.setDataSource(f.absolutePath)
                mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                mp.prepare()
                mp.start()
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { try { mp.stop() } catch (_: Exception) {} }
        }
    }

    private suspend fun speakAndroid(text: String, locale: Locale, speed: Float): Boolean {
        val t = tts ?: return false
        var waited = 0
        while (!ttsReady && waited < 3000) { kotlinx.coroutines.delay(100); waited += 100 }
        if (!ttsReady) return false
        val r = t.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (locale.language == "mr") t.setLanguage(Locale("hi", "IN")) else return false
        }
        t.setSpeechRate(speed)
        return suspendCancellableCoroutine { cont ->
            val id = "u${System.currentTimeMillis()}"
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(true) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(false) }
            })
            val res = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (res != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { tts?.stop() } catch (_: Exception) {}
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
    }
}
