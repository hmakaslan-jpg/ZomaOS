# V15.6 edge-to-edge fix

- Force fullscreen and hide navigation bar on Android 4.1+; restore immersive flags when focus returns.
- Use immersive sticky on Android 4.4+ only (API 19).
- Remove 4px lateral and 8px upper white/light border embedded in theme artwork by cropping at draw time.
- Keep the 800x480 virtual canvas and touch coordinate transform unchanged.
- Does not fix physical display overscan or manufacturer firmware letterboxing outside the app window.
- Not built or tested on the Geely head unit.
