package com.locationjoystick.app.privacy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloneStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val record = CloneRecord("com.xq7.notes", "Notes", 200)

    @Test
    fun `missing store reads null`() {
        val store = CloneStore(tmp.newFolder("a"))
        assertNull(store.read())
        assertNull(store.loadSigner())
    }

    @Test
    fun `round trip keeps record key and certificate`() {
        val store = CloneStore(tmp.newFolder("b"))
        val signer = createSigningKey(record.packageId)
        store.save(record, signer)
        assertEquals(record, store.read())
        val loaded = store.loadSigner()!!
        assertArrayEquals(signer.first.encoded, loaded.first.encoded)
        assertArrayEquals(signer.second.encoded, loaded.second.encoded)
    }

    @Test
    fun `clear removes everything`() {
        val store = CloneStore(tmp.newFolder("c"))
        store.save(record, createSigningKey(record.packageId))
        store.clear()
        assertNull(store.read())
        assertNull(store.loadSigner())
    }

    @Test
    fun `half written tmp file is ignored`() {
        val dir = tmp.newFolder("d")
        java.io.File(dir, "clone.properties.tmp").writeText("packageId=x")
        assertNull(CloneStore(dir).read())
    }

    @Test
    fun `name with newline and non ascii survives`() {
        val store = CloneStore(tmp.newFolder("e"))
        val odd = record.copy(name = "Café\nnotes")
        store.save(odd, createSigningKey(odd.packageId))
        assertEquals(odd, store.read())
    }

    @Test
    fun `payload carries record key and origin into another store`() {
        val signer = createSigningKey(record.packageId)
        val copy = CloneStore(tmp.newFolder("f"))
        assertTrue(copy.importPayload(encodePayload(record, signer, setOf("aa", "bb"))))
        assertEquals(record, copy.read())
        assertArrayEquals(signer.first.encoded, copy.loadSigner()!!.first.encoded)
        assertEquals(setOf("aa", "bb"), copy.origin())
    }

    @Test
    fun `garbage payload is refused and stores nothing`() {
        val copy = CloneStore(tmp.newFolder("g"))
        assertFalse(copy.importPayload("packageId=x".toByteArray()))
        assertNull(copy.read())
        assertEquals(emptySet<String>(), copy.origin())
    }
}
