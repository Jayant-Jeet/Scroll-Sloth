package com.halfbloodcoder.scrollsloth.detect

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.halfbloodcoder.scrollsloth.debug.TargetAppInfo

/** Version of an installed target app; fields are null when it is not installed. Visible through the manifest's queries. */
fun PackageManager.targetAppInfo(packageName: String): TargetAppInfo = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, 0)
    }
    TargetAppInfo(packageName, info.versionName, PackageInfoCompat.getLongVersionCode(info))
} catch (_: PackageManager.NameNotFoundException) {
    TargetAppInfo(packageName)
}
