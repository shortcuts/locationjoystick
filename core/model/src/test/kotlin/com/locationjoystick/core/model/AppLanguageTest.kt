package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `fromTag en returns ENGLISH`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
    }

    @Test
    fun `fromTag zh-CN returns CHINESE_SIMPLIFIED`() {
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.fromTag("zh-CN"))
    }

    @Test
    fun `fromTag zh-TW returns CHINESE_TRADITIONAL`() {
        assertEquals(AppLanguage.CHINESE_TRADITIONAL, AppLanguage.fromTag("zh-TW"))
    }

    @Test
    fun `fromTag null returns SYSTEM_DEFAULT`() {
        assertEquals(AppLanguage.SYSTEM_DEFAULT, AppLanguage.fromTag(null))
    }

    @Test
    fun `fromTag unknown tag falls back to SYSTEM_DEFAULT`() {
        assertEquals(AppLanguage.SYSTEM_DEFAULT, AppLanguage.fromTag("fr"))
    }
}
