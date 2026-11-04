package com.vilas.myai

/** Language chips. sttTag = locale sent to Google SpeechRecognizer. */
enum class LangMode(val label: String, val sttTag: String) {
    AUTO("Auto", "hi-IN"),
    HINDI("हिंदी", "hi-IN"),
    HINGLISH("Hinglish", "hi-IN"),
    MARATHI("मराठी", "mr-IN"),
    ENGLISH("English", "en-IN");
}

/** Spoken language resolved for one message. */
enum class SpeakLang { HINDI, MARATHI, ENGLISH }

object Script {
    private fun isDevanagari(c: Char) = c in '\u0900'..'\u097F'
    private fun isLatin(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    fun devanagariRatio(s: String): Float {
        var d = 0; var l = 0
        for (c in s) { if (isDevanagari(c)) d++ else if (isLatin(c)) l++ }
        return if (d + l == 0) 0f else d.toFloat() / (d + l)
    }

    fun hasDevanagari(s: String) = s.any { isDevanagari(it) }
    fun hasLatin(s: String) = s.any { isLatin(it) }

    // Very small hint list: common Marathi-only words in Devanagari.
    private val marathiHints = listOf(
        "आहे", "आहेत", "आहेस", "नाही", "काय", "कसा", "कशी", "कसे", "मला", "तुला", "आम्ही", "तुम्ही",
        "होते", "होता", "झाले", "झाला", "करतो", "करते", "आणि", "पण", "म्हणून", "इथे", "तिथे", "माझे", "माझा", "माझी", "तुझे", "जाऊ", "येतो"
    )
    fun looksMarathi(s: String): Boolean {
        val words = s.split(Regex("[\\s,.!?।]+"))
        return words.count { it in marathiHints } >= 1
    }

    // Common Roman-Hindi words, used in Auto mode to decide Hinglish vs English for typed Roman text.
    private val hinglishHints = setOf(
        "hai", "hain", "kya", "kaise", "kaisa", "kaisi", "nahi", "nahin", "mera", "meri", "mere", "tera", "tum", "aap",
        "main", "mai", "hum", "karo", "karna", "kar", "bhai", "acha", "accha", "theek", "thik", "haan", "ha", "bolo",
        "kyu", "kyun", "kab", "kahan", "kaha", "abhi", "kuch", "bahut", "bohot", "yaar", "chal", "chalo", "raha", "rahi",
        "tha", "thi", "ho", "hoga", "muze", "mujhe", "tujhe", "kaam", "naam", "ke", "ki", "ka", "ko", "se", "aur", "par"
    )
    fun looksHinglish(s: String): Boolean {
        val words = s.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        val hits = words.count { it in hinglishHints }
        return hits >= 1 && hits.toFloat() / words.size >= 0.2f
    }
}
