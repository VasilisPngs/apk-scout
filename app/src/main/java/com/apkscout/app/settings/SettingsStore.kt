package com.apkscout.app.settings

import android.content.Context

object SettingsStore {
    private const val NAME = "apkscout_settings"
    private const val INCLUDE_DEV = "include_dev"
    private const val INCLUDE_ALPHA = "include_alpha"
    private const val INCLUDE_BETA = "include_beta"
    private const val INCLUDE_RC = "include_rc"
    private const val INCLUDE_PRERELEASE = "include_prerelease"

    fun read(context: Context): ReleaseChannelSettings {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

        return ReleaseChannelSettings(
            includeDev = prefs.getBoolean(INCLUDE_DEV, false),
            includeAlpha = prefs.getBoolean(INCLUDE_ALPHA, false),
            includeBeta = prefs.getBoolean(INCLUDE_BETA, false),
            includeRc = prefs.getBoolean(INCLUDE_RC, false),
            includePrerelease = prefs.getBoolean(INCLUDE_PRERELEASE, false)
        )
    }

    fun write(
        context: Context,
        settings: ReleaseChannelSettings
    ) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(INCLUDE_DEV, settings.includeDev)
            .putBoolean(INCLUDE_ALPHA, settings.includeAlpha)
            .putBoolean(INCLUDE_BETA, settings.includeBeta)
            .putBoolean(INCLUDE_RC, settings.includeRc)
            .putBoolean(INCLUDE_PRERELEASE, settings.includePrerelease)
            .apply()
    }
}
