package org.librehu.fm

/**
 * United States RBDS: the call letters a station's PI code is computed from (NRSC-4-B, annex D.7, "Call letter
 * conversion method"), worked backwards; the cases and tables follow redsea's `getCallsignFromPI` (Oona Räisänen,
 * ISC licence), which cites the same annex. Canada and Mexico assign PI codes otherwise, and FM translators get
 * random ones: only for the US (see [appliesTo]); a station sending a wrong PI shows wrong letters.
 */
object Rbds {
    /** Countries whose stations get their PI from their call letters: the United States and its territories. */
    private val US = setOf("US", "PR", "VI", "GU", "AS", "MP", "UM")

    fun appliesTo(country: String): Boolean = country.uppercase() in US

    /** Stations with three call letters, which the computation does not cover (table of annex D). */
    private val THREE_LETTERS =
        mapOf(
            0x9950 to "KEX", 0x9951 to "KFH", 0x9952 to "KFI", 0x9953 to "KGA", 0x9954 to "KGO", 0x9955 to "KGU",
            0x9956 to "KGW", 0x9957 to "KGY", 0x9958 to "KID", 0x9959 to "KIT", 0x995A to "KJR", 0x995B to "KLO",
            0x995C to "KLZ", 0x995D to "KMA", 0x995E to "KMJ", 0x995F to "KNX", 0x9960 to "KOA", 0x9964 to "KQV",
            0x9965 to "KSL", 0x9966 to "KUJ", 0x9967 to "KVI", 0x9968 to "KWG", 0x996B to "KYW", 0x996D to "WBZ",
            0x996E to "WDZ", 0x996F to "WEW", 0x9971 to "WGL", 0x9972 to "WGN", 0x9973 to "WGR", 0x9975 to "WHA",
            0x9976 to "WHB", 0x9977 to "WHK", 0x9978 to "WHO", 0x997A to "WIP", 0x997B to "WJR", 0x997C to "WKY",
            0x997D to "WLS", 0x997E to "WLW", 0x9981 to "WOC", 0x9983 to "WOL", 0x9984 to "WOR", 0x9988 to "WWJ",
            0x9989 to "WWL", 0x9990 to "KDB", 0x9991 to "KGB", 0x9992 to "KOY", 0x9993 to "KPQ", 0x9994 to "KSD",
            0x9995 to "KUT", 0x9996 to "KXL", 0x9997 to "KXO", 0x9999 to "WBT", 0x999A to "WGH", 0x999B to "WGY",
            0x999C to "WHP", 0x999D to "WIL", 0x999E to "WMC", 0x999F to "WMT", 0x99A0 to "WOI", 0x99A1 to "WOW",
            0x99A2 to "WRR", 0x99A3 to "WSB", 0x99A4 to "WSM", 0x99A5 to "KBW", 0x99A6 to "KCY", 0x99A7 to "KDF",
            0x99AA to "KHQ", 0x99AB to "KOB", 0x99B3 to "WIS", 0x99B4 to "WJW", 0x99B5 to "WJZ", 0x99B9 to "WRC",
        )

    /** Nationally linked networks (first nibble B, D or E, the second one masked). */
    private val LINKED =
        mapOf(
            0xB001 to "NPR", 0xB002 to "CBC Radio One", 0xB003 to "CBC Radio Two", 0xB004 to "Ici Radio-Canada Première",
            0xB005 to "Ici Musique", 0xB006 to "CBC", 0xB007 to "CBC", 0xB008 to "CBC", 0xB009 to "CBC", 0xB00A to "NPR",
            0xB00B to "NPR", 0xB00C to "NPR", 0xB00D to "NPR", 0xB00E to "NPR",
        )

    /** Call letters (or network) of a US station from its PI code, or "" when the code gives none. */
    fun callSign(code: Int): String {
        var pi = code and 0xFFFF
        // Codes whose call-letter value had zero nibbles are sent with an A in front (annex D).
        pi =
            when {
                pi and 0xFFF0 == 0xAFA0 && pi and 0x000F < 0x000A -> (pi shl 12) and 0xFFFF // P1 0 0 0 -> A F A P1
                pi and 0xFF00 == 0xAF00 -> (pi shl 8) and 0xFFFF // P1 P2 0 0 -> A F P1 P2
                pi and 0xF000 == 0xA000 -> ((pi and 0x0F00) shl 4) or (pi and 0x00FF) // P1 0 P3 P4 -> A P1 P3 P4
                else -> pi
            }
        return when {
            pi in 0x9950..0x9EFF -> THREE_LETTERS[pi].orEmpty()
            pi shr 12 == 0xB || pi shr 12 == 0xD || pi shr 12 == 0xE -> LINKED[pi and 0xF0FF].orEmpty()
            pi in 0x1000..0x994F -> {
                val k = pi <= 0x54A7
                val n = pi - if (k) 0x1000 else 0x54A8
                val letter = { i: Int -> 'A' + i % 26 }
                "${if (k) 'K' else 'W'}${letter(n / 676)}${letter(n / 26)}${letter(n)}"
            }
            else -> ""
        }
    }
}
