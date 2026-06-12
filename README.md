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

`APKScout-v0.4.5.apk`

- Package ID changed to `com.apkscout.android`
- Home starts on Updates and no longer shows device hardware header
- Home filter order is Updates, User, System, All with visible count below

- Tightened APKMirror compatibility filtering and shows APK/APKM format labels

- Moves APK/APKM format labels next to the APKMirror action

- Filters APKMirror updates by Android device target such as phone, Wear OS, TV, Auto, Automotive and VR

- Filters APKMirror app and release links/titles to avoid Wear OS, TV, Auto and VR mismatches on phones

- Filters APKMirror app, release and variant metadata for Android Automotive, Android Auto, Cardboard, Daydream, Android TV and Wear OS targets

- Adds DPI-aware APKMirror variant selection with exact, nodpi/anydpi and closest-density ranking
