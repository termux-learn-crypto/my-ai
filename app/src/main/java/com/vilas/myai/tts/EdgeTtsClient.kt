package com.vilas.myai.tts

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EdgeTtsException(msg: String, val httpCode: Int = 0, val serverDateMs: Long = 0) : Exception(msg)

/** Microsoft Edge "Read aloud" neural TTS over websocket (same protocol as python edge-tts). */
object EdgeTtsClient {
    private const val BASE = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    private const val WSS = "wss://$BASE/edge/v1?TrustedClientToken=${EdgeToken.TRUSTED_CLIENT_TOKEN}"
    private val MAJOR = EdgeToken.CHROMIUM_FULL_VERSION.substringBefore('.')
    private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/$MAJOR.0.0.0 Safari/537.36 Edg/$MAJOR.0.0.0"

    @Volatile var clockSkewMs: Long = 0

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun uuid() = UUID.randomUUID().toString().replace("-", "")

    private fun jsDate(): String {
        val f = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date())
    }

    fun escapeXml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /** ratePercent: e.g. 0 = normal, 20 = +20%, -20 = slower. Returns MP3 bytes (24kHz 48kbps). */
    fun synthesize(text: String, voice: String, ratePercent: Int = 0, pitchHz: Int = 0): ByteArray {
        return try {
            synthesizeOnce(text, voice, ratePercent, pitchHz)
        } catch (e: EdgeTtsException) {
            if (e.httpCode == 403 && e.serverDateMs > 0) {
                clockSkewMs += e.serverDateMs - System.currentTimeMillis()
                synthesizeOnce(text, voice, ratePercent, pitchHz)
            } else throw e
        }
    }

    private fun synthesizeOnce(text: String, voice: String, ratePercent: Int, pitchHz: Int): ByteArray {
        val nowSec = (System.currentTimeMillis() + clockSkewMs) / 1000
        val url = "$WSS&ConnectionId=${uuid()}&Sec-MS-GEC=${EdgeToken.generate(nowSec)}" +
            "&Sec-MS-GEC-Version=${EdgeToken.SEC_MS_GEC_VERSION}"
        val req = Request.Builder().url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "muid=${uuid().uppercase()};")
            .build()

        val audio = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        var error: EdgeTtsException? = null
        val rate = (if (ratePercent >= 0) "+" else "") + "$ratePercent%"
        val pitch = (if (pitchHz >= 0) "+" else "") + "${pitchHz}Hz"

        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    "X-Timestamp:${jsDate()}\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                        "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                        "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                )
                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                    "<voice name='$voice'><prosody pitch='$pitch' rate='$rate' volume='+0%'>" +
                    escapeXml(text) + "</prosody></voice></speak>"
                webSocket.send(
                    "X-RequestId:${uuid()}\r\nContent-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${jsDate()}Z\r\nPath:ssml\r\n\r\n$ssml"
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    webSocket.close(1000, null)
                    done.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size < 2) return
                val hl = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                if (hl + 2 > data.size) return
                val headers = String(data, 2, hl, Charsets.UTF_8)
                if (headers.contains("Path:audio") && data.size > hl + 2) {
                    synchronized(audio) { audio.write(data, hl + 2, data.size - hl - 2) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code ?: 0
                val dateMs = response?.headers?.getDate("Date")?.time ?: 0L
                error = EdgeTtsException("Edge TTS failed: ${t.message ?: t.javaClass.simpleName} (http $code)", code, dateMs)
                done.countDown()
            }
        })
        val ok = done.await(25, TimeUnit.SECONDS)
        if (!ok) { ws.cancel(); throw EdgeTtsException("Edge TTS timeout") }
        error?.let { throw it }
        val bytes = synchronized(audio) { audio.toByteArray() }
        if (bytes.isEmpty()) throw EdgeTtsException("Edge TTS returned no audio")
        return bytes
    }
}
