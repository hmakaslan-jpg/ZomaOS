# ZomaOS NEXUS V7.2 — Signature Edition

Based on V7.1. Adds an actual pre-rendered premium chronograph face (signature_clock.png) with real-time Android Canvas hands, and a layered digital watch panel (signature_digital.png). Retains V7.1 music, radio, OpenAuto, OBD and original launcher behavior.

## Build
Upload the project tree to GitHub preserving `.github/workflows/build-apk.yml`; use GitHub Actions to build the debug APK. This ZIP is source code and has NOT been compiled or tested on the Geely head unit.

## Notes
- API 16 / Android 4.1; 800x480 layout.
- Clock background is a pre-rendered image, not a hand-drawn Canvas dial; moving hands remain lightweight Canvas elements.
- FM radio relies on the factory app; original player media metadata/control remain limited.
- Keep V7.1 APK as rollback. Test parked.
