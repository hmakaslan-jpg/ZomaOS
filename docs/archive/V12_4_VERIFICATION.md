# V12.4 implementation / verification

Implemented in source: 4 appearances (Electric, Pearl, Twilight, Carbon Sport); centered auto-fit clock text; opaque shortcut dock panels to prevent white artwork bleed; live drag-to-seek (ACTION_MOVE) and release-to-seek; cancellation of long press if finger leaves dock; prior Bluetooth/Wi-Fi settings shortcuts and local USB track navigation retained.

Not verified: Gradle APK build (Android SDK/Gradle not installed in this environment); physical head-unit display/touch/audio behavior; factory MCU radio frequency/metadata (not available through verified API). The factory radio app can still be launched. USB Resume logic retained, but not regression-tested on hardware.

Build via GitHub Actions workflow, then test all four themes and USB resume on actual device.
