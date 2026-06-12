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

`APKScout-v0.5.1.apk`

- Package ID changed to `com.apkscout.android`
- Home starts on Updates and no longer shows device hardware header
- Home filter order is Updates, User, System, All with visible count below

- Tightened APKMirror compatibility filtering and shows APK/APKM format labels

- Moves APK/APKM format labels next to the APKMirror action

- Filters APKMirror updates by Android device target such as phone, Wear OS, TV, Auto, Automotive and VR

- Filters APKMirror app and release links/titles to avoid Wear OS, TV, Auto and VR mismatches on phones

- Filters APKMirror app, release and variant metadata for Android Automotive, Android Auto, Cardboard, Daydream, Android TV and Wear OS targets

- Adds DPI-aware APKMirror variant selection with exact, nodpi/anydpi and closest-density ranking

- Improves APKMirror bundle detection so BUNDLE variants are shown as APKM

- Rewrites APKMirror variant selection to prefer stable release variants over beta, alpha, RC, preview and lite-release variants

- Shows APK when a compatible release contains any plain APK variant and APKM only when compatible variants are bundle-only

- Keeps device target, SDK, ABI and DPI-aware filtering in one clean APKMirror client

- Adds Home navigation scroll-to-top behavior

- Keeps installed app and update state alive while navigating between Home, Search and Settings

- Refreshes apps only on first launch or when pressing Refresh

- Introduces a cleaner Material 3 interface baseline

- Moves the app header into a persistent Material-style top area

- Refines update cards, navigation, filters, badges and action layout

- Keeps scan/update state alive across Home, Search and Settings navigation

- Refines the Material 3 baseline layout

- Moves update count into the Home filter card

- Removes duplicate page titles from Search and Settings

- Adds scroll-to-top behavior for Search navigation

- Reduces and rounds the bottom navigation container

- Fixes Material 3 navigation import for release builds

- Uses a custom rounded bottom bar item implementation without NavigationBarItem dependency
