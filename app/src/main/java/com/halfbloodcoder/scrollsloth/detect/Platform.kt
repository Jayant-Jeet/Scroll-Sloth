package com.halfbloodcoder.scrollsloth.detect

import kotlinx.serialization.Serializable

@Serializable
enum class Platform(val packageName: String, val label: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    YOUTUBE("com.google.android.youtube", "YouTube"),
    FACEBOOK("com.facebook.katana", "Facebook"),
    ;

    companion object {
        fun fromPackageName(packageName: String?): Platform? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
