# ZomaOS V11 — USB Resume Logic Review (2026-11-18)

This is a source-level engineering checkpoint, NOT an APK or an Android emulator test.

## Changes
- Persist USB file path, playback position and playing state every five seconds and before leaving the launcher.
- Resume only if the last explicitly selected audio source was USB and it was playing.
- Delay the startup attempt, retry for up to ~12 seconds if USB storage is not yet mounted.
- Restore the last file and seek to its stored position when readable.
- Mark FM as the last selected source when opening factory radio; prevent USB startup resume from taking focus away from it.
- Preserve existing radio-launch and dock logic.

## Remaining limitations
- Source code static assertions are NOT evidence of Android runtime behavior.
- Factory FM and Bluetooth playback detection are still not implemented; selecting FM is only a user action, not verified playback state.
- No Android SDK/Gradle/emulator installed in the current execution environment; APK build not run.
- UI is not pixel-verified against the three approved reference designs.
- USB unplug/replug and ignition power-cycle must ultimately be tested on the real head unit.

Do not overwrite the known-good V9.1 installation with this checkpoint.
