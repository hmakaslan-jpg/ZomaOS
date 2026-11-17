# ZomaOS NEXUS V6.2 - Media Discovery + Crash Fix

Android 4.1 / API16, 800x480. Based on V6.1.

- Retains API16-safe media button broadcasts and exception handling from V6.1.
- Adds read-only detection of common Android music metadata broadcasts. Shows track title and artist **only if** a compatible broadcast is received.
- Long press NOW PLAYING card for Media diagnostics: selected player package, detected track, and broadcast source.
- Long press MUSIC bottom button to select a player. OPEN ORIGINAL PLAYER still launches the selected app.
- No root, no MCU modification, no fabricated song data, no guarantee factory player publishes metadata.
- Visual layout preserved from V6 for safe incremental testing.

## Test
1. Build with existing GitHub Actions.
2. Park the car; start original music player and play a track.
3. Return to ZomaOS and check whether title/artist appear.
4. Long press NOW PLAYING for diagnostics.
5. Test PREV/PLAY/NEXT: they should not crash, but factory player may ignore broadcasts.

If no metadata appears, that is an informative negative result, not proof that metadata cannot be accessed through any mechanism.
