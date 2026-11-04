package com.vilas.myai.stt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vilas.myai.LangMode

enum class SttErrorKind { NO_INTERNET, NO_MATCH, MIC_DENIED, SERVICE_MISSING, LANG_MISSING, BUSY, OTHER }

interface SpeechCallbacks {
    fun onReady()
    fun onPartial(text: String)
    fun onLevel(rmsDb: Float)
    fun onFinal(text: String, detectedLang: String?)
    fun onError(kind: SttErrorKind, message: String)
    fun onEnd()
}

/** Google SpeechRecognizer wrapper. Prefers Google's recognition service (MIUI default may be Xiaomi's). */
class SpeechInput(private val context: Context, private val cb: SpeechCallbacks) {
    private var recognizer: SpeechRecognizer? = null
    private var detected: String? = null
    private var lastPartial: String = ""
    private var currentTag: String = "hi-IN"
    var listening = false
        private set

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private fun googleService(): ComponentName? {
        val pm = context.packageManager
        val services = pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        val prefs = listOf("com.google.android.googlequicksearchbox", "com.google.android.tts", "com.google.android.as")
        for (p in prefs) {
            services.firstOrNull { it.serviceInfo.packageName == p }?.let {
                return ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)
            }
        }
        return null
    }

    private fun create(): SpeechRecognizer {
        val comp = googleService()
        return if (comp != null) SpeechRecognizer.createSpeechRecognizer(context, comp)
        else SpeechRecognizer.createSpeechRecognizer(context)
    }

    fun start(mode: LangMode, lastAutoTag: String) {
        stop()
        detected = null
        lastPartial = ""
        val r = create()
        recognizer = r
        r.setRecognitionListener(listener)
        val tag = if (mode == LangMode.AUTO) lastAutoTag else mode.sttTag
        currentTag = tag
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            if (mode == LangMode.AUTO) {
                val allowed = arrayListOf("hi-IN", "mr-IN", "en-IN")
                // Android 14+ (Google service) language detection / switching. Ignored on older devices.
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, allowed)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, allowed)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("mr-IN", "en-IN"))
            }
        }
        listening = true
        try {
            r.startListening(i)
        } catch (e: Exception) {
            listening = false
            cb.onError(SttErrorKind.SERVICE_MISSING, "Speech service start nahi hua: ${e.message}")
            cb.onEnd()
        }
    }

    fun stopListening() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
    }

    fun stop() {
        listening = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = cb.onReady()
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) = cb.onLevel(rmsdB)
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) { lastPartial = t; cb.onPartial(t) }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            val text = if (t.isNullOrBlank()) lastPartial else t
            if (text.isBlank()) cb.onError(SttErrorKind.NO_MATCH, "Kuch sunai nahi diya. Mic dabake phir se bolo.")
            else cb.onFinal(text, detected ?: currentTag)
            cb.onEnd()
            stop()
        }

        override fun onLanguageDetection(results: Bundle) {
            if (Build.VERSION.SDK_INT >= 34) {
                results.getString(SpeechRecognizer.DETECTED_LANGUAGE)?.let { detected = it }
            }
        }

        override fun onError(error: Int) {
            listening = false
            if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && lastPartial.isNotBlank()) {
                cb.onFinal(lastPartial, detected ?: currentTag); cb.onEnd(); stop(); return
            }
            val (kind, msg) = when (error) {
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    SttErrorKind.NO_INTERNET to "Internet nahi hai ya slow hai. Net on karke phir bolo (ya offline language pack download karo)."
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    SttErrorKind.NO_MATCH to "Samajh nahi aaya / kuch suna nahi. Thoda saaf aur paas se bolo."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    SttErrorKind.MIC_DENIED to "Mic permission nahi hai."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    SttErrorKind.BUSY to "Speech service busy hai, 1 sec baad phir dabao."
                SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                    SttErrorKind.NO_INTERNET to "Google speech server se connect nahi hua. Phir try karo."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    SttErrorKind.LANG_MISSING to "Yeh language ($currentTag) phone ke speech service me available nahi. Google app > Settings > Voice > Offline speech recognition me pack download karo."
                SpeechRecognizer.ERROR_CLIENT ->
                    SttErrorKind.OTHER to "Mic band ho gaya. Phir dabao."
                else -> SttErrorKind.OTHER to "Speech error ($error). Phir try karo."
            }
            cb.onError(kind, msg)
            cb.onEnd()
            stop()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
