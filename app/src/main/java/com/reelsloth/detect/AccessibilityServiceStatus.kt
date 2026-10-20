package com.reelsloth.detect

object AccessibilityServiceStatus {
    /**
     * Parses Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, a ':'-separated list of
     * "package/class" entries where the class may be written relative to the package.
     */
    fun isEnabled(enabledServicesSetting: String?, packageName: String, className: String): Boolean {
        if (enabledServicesSetting.isNullOrBlank()) return false
        return enabledServicesSetting.split(':').any { entry ->
            val parts = entry.trim().split('/')
            if (parts.size != 2 || !parts[0].equals(packageName, ignoreCase = true)) return@any false
            val serviceClass = if (parts[1].startsWith('.')) packageName + parts[1] else parts[1]
            serviceClass.equals(className, ignoreCase = true)
        }
    }
}
