package org.librehu.fm

/**
 * Enhanced RadioText (eRT, open data application 0x6552): up to 128 bytes of UTF-8 (or UCS-2) text, for the scripts
 * the RDS character set lacks. The FM driver does not decode it, so it is rebuilt here from the raw groups
 * (`FmNative.readRdsGroups`): group 3A announces the group type carrying it and the encoding (bit 0 of block C:
 * UTF-8, else UCS-2), then each group of that type carries 4 bytes at segment address (block B bits 0-4) x 4; 0x0D
 * ends the text. Layout as decoded by redsea (Oona Räisänen, MIT licence), `Station::parseEnhancedRT`.
 *
 * eRT+ (0x4BD8): the RT+ tags of the eRT, same group layout as RT+ (`parseRadioTextPlus` in redsea): item toggle and
 * running bits, two tags of content type, start and length marker (characters minus one), positions in bytes of
 * the eRT. Content types 1 = item.title, 4 = item.artist.
 */
class Ert {
    /** Group type code carrying eRT (group type x 2 + version), -1 until group 3A gave it. */
    private var group = -1
    private var utf8 = true
    private val bytes = ByteArray(MAX)
    private var received = 0L

    /** Last complete text, "" until one is. */
    var text = ""
        private set

    /** Group type code carrying the eRT+ tags, and the last tags (running bit, then type, start, marker x 2). */
    private var plusGroup = -1
    private var tags: IntArray? = null

    /** Song from the eRT+ tags, "" when none. */
    var title = ""
        private set
    var artist = ""
        private set

    fun reset() {
        group = -1
        plusGroup = -1
        received = 0L
        tags = null
        text = ""
        title = ""
        artist = ""
    }

    /** [groups] as `[A, B, C, D, crc]` per group; true when [text], [title] or [artist] changed. */
    fun feed(groups: ShortArray): Boolean {
        var changed = false
        var i = 0
        while (i + 4 < groups.size) {
            val b = groups[i + 1].toInt() and 0xFFFF
            val c = groups[i + 2].toInt() and 0xFFFF
            val d = groups[i + 3].toInt() and 0xFFFF
            val crc = groups[i + 4].toInt()
            i += 5
            if (crc and BLOCK_B == 0 || crc and (BLOCK_C or BLOCK_D) != (BLOCK_C or BLOCK_D)) continue
            val code = (b shr 11) and 0x1F // group type (4 bits) and version (A = 0)
            if (code == GROUP_3A && (d == AID || d == AID_PLUS)) {
                val g = b and 0x1F
                // 00000: not carried in a group of its own, 11111: temporary data fault (ODA specification).
                if (g == 0 || g == 0x1F) continue
                if (d == AID_PLUS) {
                    plusGroup = g
                    continue
                }
                val u = c and 1 != 0
                if (g != group || u != utf8) {
                    group = g
                    utf8 = u
                    received = 0L
                }
            } else if (code == group) {
                if (segment(b and 0x1F, c, d)) changed = true
                if (song()) changed = true
            } else if (code == plusGroup) {
                tags =
                    intArrayOf(
                        (b shr 3) and 1,
                        ((b and 0x7) shl 3) or (c shr 13),
                        (c shr 7) and 0x3F,
                        (c shr 1) and 0x3F,
                        ((c and 1) shl 5) or (d shr 11),
                        (d shr 5) and 0x3F,
                        d and 0x1F,
                    )
                if (song()) changed = true
            }
        }
        return changed
    }

    private fun segment(
        address: Int,
        c: Int,
        d: Int,
    ): Boolean {
        val pos = address * 4
        val new = byteArrayOf((c shr 8).toByte(), c.toByte(), (d shr 8).toByte(), d.toByte())
        val bit = 1L shl address
        // A segment received before with other bytes: the station sends a new text.
        if (received and bit != 0L && (0..3).any { bytes[pos + it] != new[it] }) received = 0L
        new.copyInto(bytes, pos)
        received = received or bit
        // Complete once every segment up to the end marker (or all 32) arrived.
        val end = end() ?: return false
        val t = decode(end).trim()
        if (t == text) return false
        text = t
        return true
    }

    /** Title and artist cut out of the complete eRT by the eRT+ tags; true when they changed. */
    private fun song(): Boolean {
        val t = tags
        val end = end()
        var newTitle = ""
        var newArtist = ""
        if (t != null && t[0] != 0 && end != null) {
            for (k in 0..1) {
                val type = t[1 + 3 * k]
                val start = t[2 + 3 * k]
                val stop = start + t[3 + 3 * k] + 1
                if ((type != RTP_TITLE && type != RTP_ARTIST) || stop > end) continue
                val part =
                    String(bytes, start, stop - start, if (utf8) Charsets.UTF_8 else Charsets.UTF_16BE)
                        .replace("\uFFFD", "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                if (type == RTP_TITLE) newTitle = part else newArtist = part
            }
        }
        if (newTitle == title && newArtist == artist) return false
        title = newTitle
        artist = newArtist
        return true
    }

    /** Length before the end marker 0x0D (a UCS-2 character 0x000D), MAX without one, null if not received yet. */
    private fun end(): Int? {
        val step = if (utf8) 1 else 2
        var p = 0
        while (p < MAX) {
            if (received and (1L shl (p / 4)) == 0L) return null
            if (utf8 && bytes[p].toInt() == 0x0D) return p
            if (!utf8 && bytes[p].toInt() == 0 && bytes[p + 1].toInt() == 0x0D) return p
            p += step
        }
        return MAX
    }

    private fun decode(length: Int): String =
        String(bytes, 0, length, if (utf8) Charsets.UTF_8 else Charsets.UTF_16BE)
            .replace('\u0000', ' ')
            .replace(Regex("\\s+"), " ")

    private companion object {
        const val MAX = 128
        const val AID = 0x6552
        const val AID_PLUS = 0x4BD8
        const val RTP_TITLE = 1
        const val RTP_ARTIST = 4
        const val GROUP_3A = 3 shl 1
        const val BLOCK_B = 0x04
        const val BLOCK_C = 0x02
        const val BLOCK_D = 0x01
    }
}
