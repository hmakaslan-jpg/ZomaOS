# ZomaOS V1 — Android 4.1 / API 16 prototype

This is an **Android Studio source project**, not a built APK. No Android SDK/build tools were available in the authoring environment, so it has **not** been compiled or device-tested. Build it with Android Studio and Android SDK 35 (Gradle plugin 8.6.1, JDK 17). Install only on the Geely head unit after a successful build.

## V1 features
- 800×480 landscape, lightweight Canvas-based dashboard, no network calls or external assets.
- Clock, 3D-style road graphic, car silhouette, speed/RPM/coolant placeholders (explicitly not real readings).
- Five touch buttons: Android Auto, Music, Maps, Apps, Settings.
- No root, firmware, CAN or MCU access; does not replace the factory launcher.

## Important integration detail
The OpenAuto application package name is **not verified**. In `MainActivity.java`, edit the Android Auto `launch(new String[]{...})` package list to match the installed OpenAuto 0.6 package name, obtainable from Android Settings > Apps or APK inspection. The current candidate package names may not match. OpenAuto remains a separate app; no source code has been merged.

## Build
1. Open this folder as an Android Studio project.
2. Install Android SDK platform 35 and the requested Gradle plugin/JDK 17.
3. Build > Build APK(s).
4. Copy `app/build/outputs/apk/debug/app-debug.apk` to USB and install on the head unit.

If Android Studio fails to build, send the error log. Do not uninstall the known-good OpenAuto or StradaOS versions.
