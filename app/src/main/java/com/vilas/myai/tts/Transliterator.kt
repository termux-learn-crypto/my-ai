package com.vilas.myai.tts

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Roman (Hinglish / Roman Marathi) -> Devanagari using Google Input Tools, so Hindi/Marathi voices pronounce it right. */
object Transliterator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()

    /** lang = "hi" or "mr". Returns null on failure. */
    fun toDevanagari(text: String, lang: String): String? = try {
        val url = "https://inputtools.google.com/request".toHttpUrl().newBuilder()
            .addQueryParameter("text", text)
            .addQueryParameter("itc", "$lang-t-i0-und")
            .addQueryParameter("num", "1")
            .addQueryParameter("cp", "0").addQueryParameter("cs", "1")
            .addQueryParameter("ie", "utf-8").addQueryParameter("oe", "utf-8")
            .addQueryParameter("app", "demopage")
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body?.string() ?: return null
            val arr = JSONArray(body)
            if (arr.getString(0) != "SUCCESS") return null
            val sb = StringBuilder()
            val segs = arr.getJSONArray(1)
            for (i in 0 until segs.length()) {
                val seg = segs.getJSONArray(i)
                sb.append(seg.getJSONArray(1).optString(0, seg.getString(0)))
            }
            sb.toString().ifBlank { null }
        }
    } catch (e: Exception) { null }
}
