package org.librehu.fm.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.librehu.fm.WebQuality

class WebCatalogTest {
    private fun st(
        name: String,
        state: String = "",
        codec: String = "MP3",
        bitrate: Int = 128,
        clicks: Int = 0,
        tags: List<String> = emptyList(),
    ) = CatalogStation(uuid = "$name/$codec/$bitrate/$state", name = name, url = "http://x.example/$name$codec$bitrate", state = state, codec = codec, bitrate = bitrate, clicks = clicks, tags = tags)

    @Test
    fun regionSpellingsMergedCountryIsNoRegion() {
        val list =
            listOf(
                st("A", "Île-de-France"),
                st("B", "Ile de France"),
                st("C", "Ile-de-France"),
                st("D", "Bretagne"),
                st("E", "France"),
                st("F", "Europe"),
                st("G", ""),
            )
        val regions = WebCatalog.regions(list, "France")
        assertEquals(listOf("ILE DE FRANCE" to 3, "BRETAGNE" to 1), regions.map { it.key to it.count })
        // Three spellings once each: the first one met is shown.
        assertEquals("Île-de-France", regions.first().label)
        assertEquals(3, WebCatalog.entries(list, WebQuality.BEST, region = WebCatalog.NO_REGION, countryName = "France").size)
    }

    @Test
    fun streamsOfAStationGroupedAndQualityChosen() {
        val list =
            listOf(
                st("France Inter", codec = "MP3", bitrate = 128, clicks = 50),
                st("France Inter", codec = "AAC", bitrate = 192, clicks = 10),
                st("France Inter (AAC 64)", codec = "AAC", bitrate = 64, clicks = 5),
                st("France Inter", codec = "UNKNOWN", bitrate = 0, clicks = 1),
                st("Radio 105", codec = "MP3", bitrate = 128),
                st("Radio 101", codec = "MP3", bitrate = 128),
            )
        val best = WebCatalog.entries(list, WebQuality.BEST)
        assertEquals(3, best.size)
        val inter = best.single { it.station.name.startsWith("France Inter") }
        assertEquals(4, inter.streams)
        assertEquals("AAC" to 192, inter.station.codec to inter.station.bitrate)
        val eco = WebCatalog.entries(list, WebQuality.ECO).single { it.station.name.startsWith("France Inter") }
        assertEquals(64, eco.station.bitrate)
    }

    @Test
    fun flacFirstAndAacCountsMoreThanMp3() {
        val flac = st("X", codec = "FLAC", bitrate = 900)
        val aac = st("X", codec = "AAC+", bitrate = 160)
        val mp3 = st("X", codec = "MP3", bitrate = 192)
        assertEquals(flac, WebCatalog.pick(listOf(mp3, aac, flac), WebQuality.BEST))
        assertEquals(aac, WebCatalog.pick(listOf(mp3, aac), WebQuality.BEST))
    }

    @Test
    fun fmMatchNeedsTheSameName() {
        val list =
            listOf(
                st("FIP", codec = "AAC", bitrate = 192, clicks = 100),
                st("FIP", codec = "MP3", bitrate = 128, clicks = 80),
                st("FIP Rock", codec = "AAC", bitrate = 192, clicks = 30),
                st("Radio Nova", clicks = 70),
            )
        assertEquals("AAC" to 192, WebCatalog.matchFm("FIP", list, WebQuality.BEST)!!.let { it.codec to it.bitrate })
        assertEquals("MP3" to 128, WebCatalog.matchFm("FIP", list, WebQuality.ECO)!!.let { it.codec to it.bitrate })
        // A single word in common is not enough to switch the user to another station.
        assertNull(WebCatalog.matchFm("NOVA RIRE", listOf(st("Radio Nova")), WebQuality.BEST))
        assertNull(WebCatalog.matchFm("CHERIE", list, WebQuality.BEST))
    }

    @Test
    fun truncatedRdsNamesOnlyWhenNotAmbiguous() {
        val list =
            listOf(
                st("France Info", codec = "AAC", bitrate = 192, clicks = 90),
                st("France Inter", codec = "AAC", bitrate = 192, clicks = 80),
                st("Nostalgie", codec = "AAC", bitrate = 320, clicks = 60),
                st("Nostalgie Best of 80", codec = "MP3", bitrate = 128, clicks = 20),
            )
        assertNull(WebCatalog.matchFm("FRANCE I", list, WebQuality.BEST))
        assertEquals("Nostalgie", WebCatalog.matchFm("NOSTALGI", list, WebQuality.BEST)!!.name)
    }

    @Test
    fun genresByUse() {
        val list =
            listOf(
                st("A", tags = listOf("jazz", "news")),
                st("B", tags = listOf("jazz")),
                st("C", tags = listOf("jazz", "rock")),
                st("D", tags = listOf("rock")),
            )
        assertEquals(listOf("jazz" to 3, "rock" to 2), WebCatalog.genres(list, min = 2))
        assertEquals(3, WebCatalog.entries(list, WebQuality.BEST, genre = "jazz").size)
    }
}

class CatalogStoreParseTest {
    @Test
    fun apiAnswerKeepsHttpStreamsAndTags() {
        val body =
            """
            [{"stationuuid":"u1","name":" FIP ","url":"http://a/fip.m3u","url_resolved":"http://icecast.example/fip-hifi.aac",
              "favicon":"https://x/fip.png","countrycode":"FR","state":"Île-de-France","tags":"Jazz, Eclectic,",
              "codec":"AAC","bitrate":192,"hls":0,"votes":10,"clickcount":99},
             {"stationuuid":"u2","name":"No stream","url":"","url_resolved":"","favicon":"","tags":""},
             {"stationuuid":"u3","name":"HLS","url":"https://h/x.m3u8","url_resolved":"","favicon":"data:x","tags":"","hls":1}]
            """.trimIndent()
        val list = CatalogStore.parseApi(body)
        assertEquals(listOf("u1", "u3"), list.map { it.uuid })
        val fip = list[0]
        assertEquals("FIP", fip.name)
        assertEquals("http://icecast.example/fip-hifi.aac", fip.url)
        assertEquals(listOf("jazz", "eclectic"), fip.tags)
        assertEquals("rb:u1", fip.toWebStation().id)
        assertEquals("", list[1].favicon)
        assertEquals(true, list[1].hls)
    }
}
