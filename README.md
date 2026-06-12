# APKScout

APKMirror scout for installed Android apps.

## Status

Android 16+ Kotlin/Compose app.

## Current behavior

- Scans installed Android apps locally
- Shows app icon, app label, package name, version name, version code, and compact user/system status
- Checks APKMirror update data through the APKMirror API-style backend
- Shows installed version to found version when update data is available
- Shows installed version code to found version code when update data is available
- Filters prerelease update channels from Settings
- Shows Android version, DPI, and device name
- Searches installed apps by app name or package name
- Rescans installed apps and checks updates from the top refresh action
- Supports manual light/dark theme switching
- Uses bottom navigation for Home, Search, and Settings
- Search tab uses global app search without Home filters
- Opens APKMirror release pages for found updates and APKMirror search pages otherwise

## Package

`com.apkscout.android`

## Release asset

`APKScout-v0.4.0.apk`

- Package ID changed to `com.apkscout.android`
- Home starts on Updates and no longer shows device hardware header
- Home filter order is Updates, User, System, All with visible count below
