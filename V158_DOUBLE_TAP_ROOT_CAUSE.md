# V15.8 double-tap investigation

## Root cause candidate (high confidence, requires head-unit verification)
Android API 16-18 does not support immersive sticky mode. The original `applyEdgeToEdge()` set `SYSTEM_UI_FLAG_HIDE_NAVIGATION` unconditionally. On pre-KitKat Android, a touch anywhere clears the hide-navigation flag, and System UI may consume the first touch to reveal navigation. This matches the observed symptom: first touch does nothing; second touch executes.

## Patch
- Keep FULLSCREEN and LAYOUT_FULLSCREEN on API 16-18, **do not hide navigation** on these versions.
- Retain HIDE_NAVIGATION and IMMERSIVE_STICKY only on API 19+.
- Clear stale long-press callbacks on every ACTION_DOWN and use `getActionMasked()`.
- Keep five original dock tiles and the Apps shortcut in tile four.

## Trade-off
On API 16-18 devices that actually have a software navigation bar, it may now be visible. This is intentional to prevent the first-tap consumption. Most OEM car head units use hardware keys or no standard navigation bar.

## Test on device
1. Cold start and tap Settings **once**; repeat 10 times after returning to launcher.
2. Test each of five bottom buttons with one tap; test Apps and the back navigation.
3. Open another app and return; test Settings once immediately.
4. Test long-press dock customization and USB seek bar.
5. Confirm full 800x480 layout and note if any system navigation bar appears.

No Android build or on-device test was performed here. The fix is a hypothesis supported by Android platform behavior, not yet a confirmed device fix.
