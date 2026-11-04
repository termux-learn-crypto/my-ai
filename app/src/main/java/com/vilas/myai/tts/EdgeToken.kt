package com.vilas.myai.tts

/**
 * Port of edge-tts (rany2/edge-tts, drm.py) Sec-MS-GEC v2 token generator.
 * Pure Kotlin, no Android deps. Verified against python edge-tts 7.3.1.
 */
object EdgeToken {
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    const val SEC_MS_GEC_VERSION = "2-$CHROMIUM_FULL_VERSION"
    private const val WIN_EPOCH = 11644473600L

    private fun ints(vararg v: Long) = IntArray(v.size) { v[it].toInt() }

    private val SHA_INITIAL = ints(0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19)
    private val SHA_STATE = ints(0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A, 0x6C006F49, 0x04BD35AB, 0xFEB0A7E4, 0x03EFFBD2)
    private val SHA_SCHEDULE = ints(
        0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA, 0xA622EE8C, 0xD1DD9491, 0xC0DD25A0,
        0xF9E70AB6, 0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33, 0xD258B060, 0x72766564
    )
    private val K = ints(
        0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
        0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
        0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
        0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
        0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
        0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
        0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
        0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2
    )
    private const val SHA_ADJ = 0xC9DC06A7L
    private val K_BLOCK = K.copyOf().also { it[14] = SHA_ADJ.toInt() }
    private const val SIGMA0_ADJ = 0x059C0B80
    private const val SIGMA1_ADJ = 0x000B92C0

    private val CS64_COEF = ints(
        0xE82E91B1, 0x44DE24C6, 0x3BEB65BC, 0xC89C5933, 0x8D1EC211, 0x314EF5D5, 0xE92491B1,
        0xF0A5B9D5, 0x06064CF3, 0xDA79FA60, 0xBED20560, 0x89F9C9F8, 0x35FA38C6, 0xCB49E3C6
    )
    private val CS64_OFF = ints(
        0xB32700D0, 0x88DFF51D, 0x29514038, 0xB328CBDF, 0xD405B4D5, 0x26E55F3C, 0x607801C6,
        0x82B2C1FB, 0xFA51A3DA, 0x207CC464, 0x3C3C74F3, 0xD2D14B61, 0xBB04A2C1, 0xC5626167
    )
    private const val CS64_FACTOR = 0x82893933L
    private const val CS64_ADJ0 = 0xF2F90B1AL
    private const val CS64_ADJ1 = 0x0B1A0000

    private fun rotr(v: Int, n: Int) = Integer.rotateRight(v, n)
    private fun rol16(v: Int) = Integer.rotateLeft(v, 16)
    private fun s0(v: Int) = rotr(v, 7) xor rotr(v, 18) xor (v ushr 3)
    private fun s1(v: Int) = rotr(v, 17) xor rotr(v, 19) xor (v ushr 10)

    private fun rounds(state: IntArray, w: IntArray, first: Int): IntArray {
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        val k = if (first == 14) K_BLOCK else K
        for (i in first until 64) {
            val t1 = h + (rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)) + (g xor (e and (f xor g))) + k[i] + w[i]
            val t2 = (rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)) + ((a and b) or (c and (a or b)))
            h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        return intArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun expand(w: IntArray, firstWord: Int) {
        for (i in firstWord until 64) w[i] = s1(w[i - 2]) + w[i - 7] + s0(w[i - 15]) + w[i - 16]
    }

    private fun be(data: ByteArray, off: Int, len: Int): Int {
        var r = 0
        for (i in 0 until len) r = (r shl 8) or (data[off + i].toInt() and 0xFF)
        return r
    }

    private fun shaInit(d7: ByteArray): IntArray {
        val w = IntArray(64)
        w[14] = be(d7, 0, 3)
        w[15] = be(d7, 3, 4)
        for (i in 16..29) {
            var v = SHA_SCHEDULE[i - 16]
            if (i - 2 >= 14) v += if (i == 16) (s1(w[i - 2]) xor SIGMA1_ADJ) else s1(w[i - 2])
            if (i - 7 >= 14) v += w[i - 7]
            if (i - 15 >= 14) v += if (i == 29) (s0(w[i - 15]) xor SIGMA0_ADJ) else s0(w[i - 15])
            w[i] = v
        }
        w[30] = s1(w[28]) + w[23] + s0(w[15]) + w[14] + SHA_ADJ.toInt() - K[14]
        expand(w, 31)
        val working = rounds(SHA_STATE, w, 14)
        return IntArray(8) { SHA_INITIAL[it] + working[it] }
    }

    private fun shaDigest(data: ByteArray): ByteArray {
        val length = 57 + data.size
        val zeros = Math.floorMod(55 - length, 64)
        val rest = data.copyOfRange(7, data.size)
        val padded = ByteArray(rest.size + 1 + zeros + 8)
        System.arraycopy(rest, 0, padded, 0, rest.size)
        padded[rest.size] = 0x80.toByte()
        val bits = length.toLong() * 8
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bits ushr (8 * i)).toByte()
        var state = shaInit(data.copyOfRange(0, 7))
        var off = 0
        while (off < padded.size) {
            val w = IntArray(64)
            for (i in 0 until 16) w[i] = be(padded, off + i * 4, 4)
            expand(w, 16)
            val working = rounds(state, w, 0)
            state = IntArray(8) { state[it] + working[it] }
            off += 64
        }
        val out = ByteArray(32)
        for (i in 0 until 8) for (j in 0 until 4) out[i * 4 + j] = (state[i] ushr (24 - 8 * j)).toByte()
        return out
    }

    private fun le(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8) or
            ((data[off + 2].toInt() and 0xFF) shl 16) or ((data[off + 3].toInt() and 0xFF) shl 24)

    private fun cs64(data: ByteArray, digest: ByteArray): IntArray {
        val keyA = le(digest, 0) or 1
        val keyB = le(digest, 4) or 1
        val f0 = keyA * CS64_FACTOR.toInt()
        val f1 = keyB * CS64_FACTOR.toInt()
        val terms = ArrayList<Int>()
        val offsets = ArrayList<Int>()
        for (i in CS64_COEF.indices) {
            terms.add(CS64_COEF[i] * (if (i % 2 == 0) f0 else f1))
            offsets.add(CS64_OFF[i])
        }
        val src = ByteArray(1 + data.size)
        System.arraycopy(data, 0, src, 1, data.size)
        val padLen = src.size + Math.floorMod(-src.size, 4)
        val padded = src.copyOf(padLen)
        val words = ArrayList<Int>()
        var o = 0
        while (o < padded.size) { words.add(le(padded, o)); o += 4 }
        if (words.size % 2 == 1) words.add(0)
        for ((idx, word) in words.withIndex()) {
            var term = word * (if (idx % 2 == 0) keyA else keyB)
            var offset = rol16(word) * (if (idx % 2 == 0) 0xEF0569FBL.toInt() else 0xC31713DBL.toInt())
            if (idx == 0) {
                term += CS64_ADJ0.toInt() * f0
                offset += CS64_ADJ1
            }
            terms.add(term); offsets.add(offset)
        }
        var swapAcc = 0; var swapLast = 0; var state = 0; var acc = 0
        var i = 0
        while (i < terms.size) {
            val x = terms[i]; val y = terms[i + 1]
            var t = rol16(offsets[i] + x)
            var q = t * 0x76F50F34
            swapAcc += rol16(q) * 0xC3EFEA97L.toInt() + q
            t = rol16(offsets[i + 1] + y)
            q = t * 0x1881CE3C
            swapLast = rol16(q) * 0x35BD1EC9 + q
            swapAcc += swapLast
            state = state * keyA + x
            state = rol16(state) * 0xCF98B111L.toInt()
            state = rol16(state) * 0x87085B9FL.toInt()
            state = rol16(state) * 0x12CEB96D
            state = rol16(state) * 0x257E1D83
            acc += state
            t = state * keyB + y
            t = rol16(t) * 0xA27416F5L.toInt()
            t = rol16(t) * 0xD38396FFL.toInt()
            state = rol16(rol16(t) * 0x7C932B89) * 0xBFA49F69L.toInt()
            acc += state
            i += 2
        }
        return intArrayOf(swapLast xor state, swapAcc xor acc)
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    /** unixSeconds: current unix time in seconds (already skew-corrected). */
    fun generate(unixSeconds: Long): String {
        var s = unixSeconds + WIN_EPOCH
        s -= Math.floorMod(s, 300L)
        val ticks = s * 10_000_000L
        val data = "$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.US_ASCII)
        val digest = shaDigest(data)
        val c = cs64(data, digest)
        val cb = ByteArray(8)
        for (k in 0 until 4) { cb[k] = (c[0] ushr (8 * k)).toByte(); cb[4 + k] = (c[1] ushr (8 * k)).toByte() }
        return hex(cb) + "-" + hex(digest)
    }
}
