package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchAfterLinkTest {
    private val installed = setOf("com.nianticlabs.pokemongo", "com.nianticlabs.pikmin", "other")

    private fun pick(
        stored: String?,
        chosen: Boolean,
        path: GuidedPath,
        isInstalled: (String) -> Boolean = { it in installed },
    ) = effectiveLaunchPackage(stored, chosen, path, isInstalled)

    @Test
    fun `explicit app wins over the path`() {
        assertEquals("other", pick("other", true, GuidedPath.POKEMON_GO))
    }

    @Test
    fun `explicit none stays none on a game path`() {
        assertNull(pick(null, true, GuidedPath.POKEMON_GO))
    }

    @Test
    fun `unset follows the path game when installed`() {
        assertEquals("com.nianticlabs.pokemongo", pick(null, false, GuidedPath.POKEMON_GO))
        assertEquals("com.nianticlabs.pikmin", pick(null, false, GuidedPath.PIKMIN_BLOOM))
    }

    @Test
    fun `unset with the game missing or default path is none`() {
        assertNull(pick(null, false, GuidedPath.POKEMON_GO) { false })
        assertNull(pick(null, false, GuidedPath.DEFAULT))
    }
}
