# APKScout

APKMirror update scout for Android apps.

## Status

Early Android 16+ Kotlin/Compose app.

## Current behavior

- Scans installed Android apps locally
- Opens APKMirror search pages
- Opens APKMirror pages per app
- Handles APKMirror automated-check blocking explicitly
- Filters regular APK candidates
- Rejects unsupported bundle-style formats by default
- Compares compatible APK version codes when available
- Caches successful APKMirror checks for 15 minutes
- Supports per-app APKMirror refresh to bypass cache
- Batch automated checks are disabled because APKMirror blocks automated requests

## Package

`com.apkscout.app`

## Release asset

`APKScout-v0.2.0.apk`
