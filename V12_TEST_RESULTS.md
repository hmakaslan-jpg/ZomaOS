# ZomaOS V12 engineering checkpoint

## Changes applied
- Radio launch preserves factory application and avoids undocumented MCU controls or invented FM frequency.
- Source switch to FM is recorded only after startActivity succeeds.
- USB Next/Previous selects an adjacent track without calling seekTo; supports uninitialized index.
- Version bumped to 12.0 source checkpoint.

## Tests / limitations
- Static source assertions passed for method separation and expected source changes.
- Android APK build and runtime tests NOT performed (Android SDK/Gradle unavailable here).
- Factory radio frequency read/preset control requires documented interface or device-level inspection.
- USB hardware button interpretation and touch hit areas require real head-unit testing.
- This is a source checkpoint, not an installable APK. Keep the known-good installation.
