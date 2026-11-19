# ZomaOS V12.6 — Live UI Engineering Preview

## Implemented in source
- Replaced baked-in full-screen Concept background in the active `drawApproved()` path with live Canvas panels, labels, shortcut cards, clocks and music controls.
- Kept four appearance variants: Electric Night, Pearl Day, Twilight, Carbon Sport.
- Removed static/illustrative song, clock and dock button overlays from the active drawing path.
- Added a lightweight vector vehicle silhouette; this preview intentionally uses **no photographic city/car background** to test the performance-first approach.
- Preserved existing USB resume/player code, radio and Android Auto launch functions, and five dock customization preferences.
- Updated dock and player touch areas for the new positions.

## Limitations / NOT verified
- This is an **engineering preview**, not a pixel-perfect reproduction of the selected V12.6 concept image. The visual design is simplified for reliable dynamic widgets.
- No APK build or real-head-unit runtime test was performed in this environment. GitHub Actions build and on-device test are required.
- Weather data and native radio frequency integration are not implemented.
- Wi-Fi/BT text markers are not live connectivity status indicators; taps still open Android settings.
- The previous V12.5 source ZIP remains the rollback reference.
