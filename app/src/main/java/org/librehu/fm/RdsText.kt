package org.librehu.fm

/**
 * Text of the RDS: programme service name (PS), radiotext (RT) and the RT+ tags cut out of the radiotext.
 *
 * Character set of EN 50067:1998 annex E (IEC 62106): the bytes above 0x7F are letters with accents and symbols
 * (0x82 = é, 0x8B = Ç, 0xA9 = €…); the table below follows the annex as reproduced by redsea (Oona Räisänen, MIT
 * licence). 0x20–0x7E stay ASCII, as the encoders send them (the annex has ¤ ― ‖ ¯ instead of $ ^ ` ~). The native
 * library used to turn every byte above 0x7E into a space ("Vous  coutez").
 */
object RdsText {
    /** Bytes 0x80 to 0xFF, sixteen per line. */
    private const val HIGH =
        "áàéèíìóòúùÑÇŞβ¡Ĳ" +
            "âäêëîïôöûüñçşǧıĳ" +
            "ªα©‰Ǧěňőπ€£\$←↑→↓" +
            "º¹²³±İńűµ¿÷°¼½¾§" +
            "ÁÀÉÈÍÌÓÒÚÙŘČŠŽÐĿ" +
            "ÂÄÊËÎÏÔÖÛÜřčšžđŀ" +
            "ÃÅÆŒŷÝÕØÞŊŔĆŚŹŦð" +
            "ãåæœŵýõøþŋŕćśźŧ "

    /** RT+ content types (RDS Forum RT+ specification): item.title and item.artist. */
    private const val RTP_TITLE = 1
    private const val RTP_ARTIST = 4

    /**
     * One character per byte, so that the positions of the RT+ tags stay right: control codes and NUL become spaces,
     * 0x1F a soft hyphen, 0x0D (end of the radiotext) ends the text.
     */
    fun decode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            when {
                c == 0x0D -> break
                c == 0x1F -> sb.append('­')
                c < 0x20 || c == 0x7F -> sb.append(' ')
                c < 0x80 -> sb.append(c.toChar())
                else -> sb.append(HIGH[c - 0x80])
            }
        }
        return sb.toString()
    }

    /** For display: no soft hyphen, no repeated spaces. */
    fun clean(text: String): String = text.replace("­", "").trim().replace(Regex("\\s+"), " ")

    /**
     * Title and artist given by the RT+ tags [tags] (`FmNative.getRtPlus`: toggle, running, then content type, start
     * and length marker of two tags) in the radiotext [rt] (from [decode]); null when the item is not running or no
     * tag fits. The length marker is the number of characters minus one ("Z100 – Fireflies – OWL CITY": title at 7,
     * marker 8; artist at 19, marker 7).
     */
    fun rtPlus(
        rt: String,
        tags: IntArray,
    ): Pair<String, String>? {
        if (tags.size < 8 || tags[1] == 0) return null
        var title = ""
        var artist = ""
        for (i in 0..1) {
            val type = tags[2 + 3 * i]
            val start = tags[3 + 3 * i]
            val end = start + tags[4 + 3 * i] + 1
            if (type != RTP_TITLE && type != RTP_ARTIST) continue
            if (start < 0 || end > rt.length) continue
            val text = clean(rt.substring(start, end))
            if (type == RTP_TITLE) title = text else artist = text
        }
        return if (title.isEmpty() && artist.isEmpty()) null else title to artist
    }
}
