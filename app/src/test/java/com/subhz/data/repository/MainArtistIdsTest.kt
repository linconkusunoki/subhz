package com.subhz.data.repository

import com.subhz.data.api.Album
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the album paging that decides which artists count as main artists, including the
 * termination conditions that a server ignoring offset would otherwise turn into a spin.
 */
class MainArtistIdsTest {

    @Test
    fun `walks every page and collects album artists`() = runBlocking {
        val pages = mapOf(
            0 to List(500) { Album("a$it", "A$it", artistId = "headliner") },
            500 to listOf(Album("b1", "B1", artistId = "guest"), Album("b2", "B2")),
        )

        val ids = mainArtistIds { offset -> pages[offset] ?: emptyList() }

        assertEquals(setOf("headliner", "guest"), ids)
    }

    @Test
    fun `stops when the server replays a full page instead of advancing`() = runBlocking {
        val page = List(500) { Album("a$it", "A$it", artistId = "headliner") }
        val asked = mutableListOf<Int>()

        val ids = mainArtistIds { offset ->
            asked += offset
            page
        }

        assertEquals(listOf(0, 500), asked)
        assertEquals(setOf("headliner"), ids)
    }
}
