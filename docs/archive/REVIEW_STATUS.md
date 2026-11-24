# V11 Full Bleed Preview — NOT FINAL

Source baseline: V11 Redesign Preview, preserving V9 media and shortcut logic.

## Implemented
- Full-bleed car scene and translucent gradient rather than opaque UI cards.
- Live digital/analog clock, track labels, and playback controls.
- No decorative bottom status/footer; actual dock is at y=415–480.
- Touch positions aligned with drawn playback buttons, dock and seekbar.
- Build workflow artifact renamed to V11.

## Not verified / limitations
- No Android SDK / Gradle runtime available in this environment; APK build not executed.
- Preview screenshots are approximations made with Pillow, not Android screenshots.
- Radio and Bluetooth playback state detection remains unavailable; UI does not fabricate it.
- The approved original design is a raster image, not a layered Figma/PSD source; exact pixel match not verified.
- Device testing, factory FM and USB restart behavior still needed.
