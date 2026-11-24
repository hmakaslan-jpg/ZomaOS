# ZomaOS V12.6.1 — UI fixes (source review)

## Fixed in source
- Seek uses one 800×480 coordinate range (`197..434`) for drawing, ACTION_DOWN, MOVE and UP. Position is clamped to [0,1].
- Seek drag only starts when localPlayer exists; no false seek on a blank bar.
- Left sidebar touch rows now match the seven 56px rows drawn at y=58..450.
- Wi-Fi and Bluetooth hit zones now match their rendered header text.
- Music playback controls now show only PREV, PLAY/PAUSE and NEXT, matching the three implemented touch actions. Removed decorative controls without implemented actions.
- Version code/name incremented to 31 / 12.6.1-ui-fixes.

## Preserved
- Four theme choices, live cards and clocks, existing USB Resume, factory radio launch, dock customization.

## Not yet proven
- APK build, runtime performance, real Geely head-unit behavior, visual fidelity to the approved cinematic concept, radio frequency metadata.
- This engineering-preview UI uses a vector car silhouette; it is NOT the approved city/car cinematic concept.

## Validation
- Source invariants checked locally (seek geometry, menu mapping, version metadata).
- ZIP integrity verified.
- No Android SDK/Gradle build executed in this environment.
