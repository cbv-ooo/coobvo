package dev.coobvo.prismmedia

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_PRISM = Compatibility(
        name = "Prism",
        packageName = "com.prism.music",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4438,
        targets = listOf(
            // Exact source build inspected; later targets remain unsupported until re-fingerprinted.
            AppTarget(version = "1.8.2.1")
        )
    )
}
