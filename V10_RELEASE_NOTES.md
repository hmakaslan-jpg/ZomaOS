# ZomaOS NEXUS V10 — TEST SOURCE

Base: user-provided V9 Stabilization source. Android minSdk 16, targetSdk 22, 800x480 canvas.

## Changes
- Redesigned both dashboard themes with runtime-drawn controls and text; Daylight Pearl Titanium and Night Calm Navy. Uses actual existing Geely X7 photo asset. Visual layout is an implementation, not an exact pixel reproduction of the illustrative V10 mockup.
- Media source selector: USB, factory FM, factory Bluetooth (package names are best-effort). FM/BT source is explicitly SELECTED, **not verified playing**; MCU status integration remains unimplemented.
- USB resume: saves file path and playback position periodically and on pause, attempts restore after startup if USB exists and was playing, and if no external source was selected. Startup resume can be disabled in Settings. Real power-cycle testing required.
- Removes legacy concept image backgrounds, and compresses photos to reduce ZIP/APK resources.
- Preserves original USB, OBD, app shortcuts, maintenance logic.

## Not verified
- No Android APK build or physical head-unit testing performed here.
- Factory Bluetooth package names and playback-state integration are not verified.
- FM frequency and BT metadata are not available; never fabricated.
- Auto-resume timing depends on factory USB mount timing.

## Build
Upload contents of project directory (including hidden .github) to GitHub repository root, then run Actions workflow.
