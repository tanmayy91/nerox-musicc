---
name: Package rename — vivi → nerox
description: Complete record of what was renamed and what was intentionally left unchanged
---

## What changed
- `com.music.vivi` → `com.music.nerox` in ALL Kotlin source files (app, canvas, vivimusiccanvas, applecanvas, betterlyrics, artistvideo modules)
- Source directories renamed: `app/src/main/kotlin/com/music/vivi/` → `com/music/nerox/`; same for gms/foss source sets and all library modules
- `applicationId = "com.vivi.vivimusic"` → `"com.nerox.neroxmusic"` in `app/build.gradle.kts`
- `namespace = "com.music.vivi"` → `"com.music.nerox"` in `app/build.gradle.kts`
- `artistvideo/build.gradle.kts` namespace updated to `com.music.nerox.artistvideo`
- `app/proguard-rules.pro` — all keep rules updated to `com.music.nerox.*`
- `app/src/main/res/xml/shortcuts.xml` — targetPackage/targetClass/action strings updated
- `app/src/main/AndroidManifest.xml` — widget action strings and cast provider updated

## What was NOT changed (intentional)
- `vivimusic_settings` SharedPreferences key in `DensityScaler.kt` — changing breaks user data
- `vivimusic-listen-together.onrender.com` server hostname — external server, cannot rename
- `android:scheme="vivimusic"` intent filter in manifest — protocol identity, changing breaks deep links
- Function names `vivimusicApp`, `vivimusicTheme` in MainActivity/Theme.kt — internal Kotlin function names, safe to leave
- Module names in `settings.gradle.kts` (`:vivimusiccanvas`, `:canvas`) — these are Gradle project names, not package names; no change needed

**Why:** User explicitly requested full package rename to "nerox". Gradle project names and deep-link schemes are external contracts that can't be trivially renamed.
