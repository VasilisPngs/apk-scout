package com.apkscout.app.settings

object ReleaseChannelFilter {
    private val devRegex = Regex("\\b(dev|nightly|canary|snapshot|internal)\\b", RegexOption.IGNORE_CASE)
    private val alphaRegex = Regex("\\balpha\\b", RegexOption.IGNORE_CASE)
    private val betaRegex = Regex("\\bbeta\\b", RegexOption.IGNORE_CASE)
    private val rcRegex = Regex("\\b(rc|release candidate)\\b", RegexOption.IGNORE_CASE)
    private val prereleaseRegex = Regex("\\b(pre[\\s-]?release|preview|early access)\\b", RegexOption.IGNORE_CASE)

    fun isAllowed(
        versionName: String,
        settings: ReleaseChannelSettings
    ): Boolean {
        val value = versionName.trim()

        if (value.isBlank()) return true
        if (devRegex.containsMatchIn(value) && !settings.includeDev) return false
        if (alphaRegex.containsMatchIn(value) && !settings.includeAlpha) return false
        if (betaRegex.containsMatchIn(value) && !settings.includeBeta) return false
        if (rcRegex.containsMatchIn(value) && !settings.includeRc) return false
        if (prereleaseRegex.containsMatchIn(value) && !settings.includePrerelease) return false

        return true
    }
}
