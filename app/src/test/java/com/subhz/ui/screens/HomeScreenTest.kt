package com.subhz.ui.screens

import com.subhz.data.api.Album
import com.subhz.data.api.Artist
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the home greeting buckets. The hour comes from the device clock, so the boundaries
 * are the only thing that can silently drift.
 */
class HomeScreenTest {

    @Test
    fun `greeting follows the time of day`() {
        assertEquals("Good evening", greetingFor(0))
        assertEquals("Good morning", greetingFor(5))
        assertEquals("Good morning", greetingFor(11))
        assertEquals("Good afternoon", greetingFor(12))
        assertEquals("Good afternoon", greetingFor(17))
        assertEquals("Good evening", greetingFor(18))
        assertEquals("Good evening", greetingFor(23))
    }

    @Test
    fun `entries without cover art never reach the home tiles`() {
        val albums = listOf(
            Album(id = "1", name = "With art", coverArt = "1"),
            Album(id = "2", name = "No art"),
            Album(id = "3", name = "Blank art", coverArt = ""),
        )
        assertEquals(listOf("1"), albums.withImage().map { it.id })

        val artists = listOf(
            Artist(id = "a", name = "With art", coverArt = "a"),
            Artist(id = "b", name = "No art"),
        )
        assertEquals(listOf("a"), artists.withArtistImage().map { it.id })
    }
}
