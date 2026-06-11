package com.apkscout.app.settings

data class ReleaseChannelSettings(
    val includeDev: Boolean = false,
    val includeAlpha: Boolean = false,
    val includeBeta: Boolean = false,
    val includeRc: Boolean = false,
    val includePrerelease: Boolean = false
)
