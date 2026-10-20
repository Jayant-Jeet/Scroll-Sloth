package com.halfbloodcoder.scrollsloth.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStatusTest {
    private val pkg = "com.halfbloodcoder.scrollsloth"
    private val cls = "com.halfbloodcoder.scrollsloth.detect.ReelAccessibilityService"

    @Test
    fun detectsFullyQualifiedEntry() {
        assertTrue(AccessibilityServiceStatus.isEnabled("other/x.Y:$pkg/$cls", pkg, cls))
    }

    @Test
    fun detectsRelativeClassName() {
        assertTrue(AccessibilityServiceStatus.isEnabled("$pkg/.detect.ReelAccessibilityService", pkg, cls))
    }

    @Test
    fun rejectsMissingOrOtherServices() {
        assertFalse(AccessibilityServiceStatus.isEnabled(null, pkg, cls))
        assertFalse(AccessibilityServiceStatus.isEnabled("", pkg, cls))
        assertFalse(AccessibilityServiceStatus.isEnabled("com.other/$cls", pkg, cls))
        assertFalse(AccessibilityServiceStatus.isEnabled("$pkg/$cls.Extra", pkg, cls))
    }
}
