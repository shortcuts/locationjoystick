package com.locationjoystick.feature.map.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveResultTest {
    private val failed = SaveResult.Failed(7)

    @Test
    fun `succeeded saves from any state`() {
        listOf(SaveResult.NotSaved, SaveResult.Saved, failed).forEach {
            assertEquals(SaveResult.Saved, it.after(SaveEvent.Succeeded))
        }
    }

    @Test
    fun `rejected replaces a saved result so saved and failed never coexist`() {
        assertEquals(failed, SaveResult.Saved.after(SaveEvent.Rejected(7)))
        assertEquals(failed, SaveResult.NotSaved.after(SaveEvent.Rejected(7)))
    }

    @Test
    fun `edited clears saved and failed`() {
        assertEquals(SaveResult.NotSaved, SaveResult.Saved.after(SaveEvent.Edited))
        assertEquals(SaveResult.NotSaved, failed.after(SaveEvent.Edited))
    }

    @Test
    fun `edited on not saved is rejected`() {
        assertNull(SaveResult.NotSaved.after(SaveEvent.Edited))
    }
}
