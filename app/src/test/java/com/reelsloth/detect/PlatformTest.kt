package com.reelsloth.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformTest {
    @Test
    fun platformPackageNamesMatchSupportedApps() {
        assertEquals("com.instagram.android", Platform.INSTAGRAM.packageName)
        assertEquals("com.google.android.youtube", Platform.YOUTUBE.packageName)
        assertEquals("com.facebook.katana", Platform.FACEBOOK.packageName)
    }

    @Test
    fun fromPackageNameFindsOnlySupportedApps() {
        Platform.entries.forEach { assertEquals(it, Platform.fromPackageName(it.packageName)) }
        assertNull(Platform.fromPackageName("com.android.systemui"))
        assertNull(Platform.fromPackageName(null))
    }
}
