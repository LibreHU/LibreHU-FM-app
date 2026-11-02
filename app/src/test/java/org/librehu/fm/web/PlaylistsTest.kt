package org.librehu.fm.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {
    @Test
    fun extendedM3uKeepsNames() {
        val text =
            """
            #EXTM3U
            #EXTINF:-1,Radio One
            http://stream.example.org:8000/one.mp3
            # a comment
            #EXTINF:-1 tvg-logo="x.png",Radio Two
            https://example.net/two.aac
            """.trimIndent()
        assertEquals(
            listOf(
                PlaylistEntry("http://stream.example.org:8000/one.mp3", "Radio One"),
                PlaylistEntry("https://example.net/two.aac", "Radio Two"),
            ),
            Playlists.parse(text),
        )
    }

    @Test
    fun plainM3uWithBomAndCrlf() {
        val text = "﻿http://a.example/live\r\nhttp://b.example/live\r\nhttp://a.example/live\r\n"
        assertEquals(listOf("http://a.example/live", "http://b.example/live"), Playlists.parse(text).map { it.url })
        assertTrue(Playlists.parse(text).all { it.name.isEmpty() })
    }

    @Test
    fun plsInFileOrder() {
        val text =
            """
            [playlist]
            NumberOfEntries=2
            File2=http://backup.example/stream
            Title2=Backup
            File1=http://main.example/stream
            Title1=Main
            Length1=-1
            Version=2
            """.trimIndent()
        assertEquals(Playlists.Kind.PLS, Playlists.kindOfText(text))
        assertEquals(
            listOf(PlaylistEntry("http://main.example/stream", "Main"), PlaylistEntry("http://backup.example/stream", "Backup")),
            Playlists.parse(text),
        )
    }

    @Test
    fun hlsIsAStreamNotAList() {
        val text =
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"
            https://cdn.example/radio/128/index.m3u8
            """.trimIndent()
        assertEquals(Playlists.Kind.HLS, Playlists.kindOfText(text))
        assertTrue(Playlists.parse(text).isEmpty())
    }

    @Test
    fun kindsFromAddressAndContentType() {
        assertEquals(Playlists.Kind.PLS, Playlists.kindOfUrl("http://x.example/listen.pls?sid=1"))
        assertEquals(Playlists.Kind.M3U, Playlists.kindOfUrl("https://x.example/live.M3U"))
        assertEquals(Playlists.Kind.NONE, Playlists.kindOfUrl("https://x.example/live.mp3"))
        assertEquals(Playlists.Kind.PLS, Playlists.kindOfContentType("audio/x-scpls; charset=utf-8"))
        assertEquals(Playlists.Kind.M3U, Playlists.kindOfContentType("application/vnd.apple.mpegurl"))
        assertEquals(Playlists.Kind.NONE, Playlists.kindOfContentType("audio/mpeg"))
    }

    @Test
    fun onlyHttpAddressesAndHostAsName() {
        assertEquals(emptyList<PlaylistEntry>(), Playlists.parse("file:///sdcard/a.mp3\nrtsp://x/y\n"))
        assertEquals("stream.example.org", Playlists.nameFromUrl("http://user@stream.example.org:8000/live"))
    }

    @Test
    fun latin1FallbackAndRetryDelays() {
        val latin1 = "#EXTM3U\n#EXTINF:-1,Fréquence\nhttp://f.example/s\n".toByteArray(Charsets.ISO_8859_1)
        assertEquals("Fréquence", Playlists.parse(WebPlayer.decode(latin1)).single().name)
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 30_000L), (0..5).map { WebPlayer.delayFor(it) })
    }
}
