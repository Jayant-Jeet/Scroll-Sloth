package com.halfbloodcoder.scrollsloth.detect

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityConfigTest {
    private val attributes = run {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder()
            .parse(File("src/main/res/xml/accessibility_service_config.xml"))
        val element = document.documentElement
        val ns = "http://schemas.android.com/apk/res/android"
        listOf(
            "packageNames", "canRetrieveWindowContent", "accessibilityFlags",
            "accessibilityEventTypes", "notificationTimeout",
        ).associateWith { element.getAttributeNS(ns, it) }
    }

    @Test
    fun serviceIsLimitedToExactlyTheThreeSupportedApps() {
        assertEquals(
            Platform.entries.map { it.packageName }.toSet(),
            attributes.getValue("packageNames").split(',').map { it.trim() }.toSet(),
        )
        assertEquals(3, attributes.getValue("packageNames").split(',').size)
    }

    @Test
    fun inspectorCapabilitiesAreConfigured() {
        assertEquals("true", attributes.getValue("canRetrieveWindowContent"))
        assertEquals("100", attributes.getValue("notificationTimeout"))
        assertTrue("flagReportViewIds" in attributes.getValue("accessibilityFlags").split('|'))
        assertEquals(
            setOf("typeWindowContentChanged", "typeViewScrolled", "typeWindowStateChanged"),
            attributes.getValue("accessibilityEventTypes").split('|').toSet(),
        )
    }
}
