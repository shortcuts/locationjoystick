package com.locationjoystick.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrivacyFlagsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `missing file reads both flags false`() {
        assertEquals(PrivacyFlags(false, false), PrivacyFlagsStore(File(tmp.root, "x/flags.properties")).read())
    }

    @Test
    fun `round trip keeps both flags`() {
        val store = PrivacyFlagsStore(File(tmp.root, "x/flags.properties"))
        store.write(PrivacyFlags(enabled = true, disclosureAccepted = true))
        assertEquals(PrivacyFlags(true, true), store.read())
        store.write(PrivacyFlags(enabled = false, disclosureAccepted = true))
        assertEquals(PrivacyFlags(false, true), store.read())
    }

    @Test
    fun `half written tmp file is ignored`() {
        val file = File(tmp.root, "flags.properties")
        File(tmp.root, "flags.properties.tmp").writeText("enabled=true")
        assertEquals(PrivacyFlags(), PrivacyFlagsStore(file).read())
    }
}
