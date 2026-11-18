# ZomaOS V11 Exact-Reference Prototype

Three approved 800x480 reference artworks are embedded as base assets, with dynamic clocks, media title, and live hit zones drawn over them. This preserves the original art instead of approximating its glow/gradients in Canvas.

**Important limitations:** Some static decorative glyphs/labels remain part of the source artwork. Dynamic areas are inpainted, so their local pixels are not identical. This is a prototype, NOT a pixel-perfect independently-layered native implementation. FM/BT playback detection and real radio frequency are NOT implemented. Existing V9 audio behavior is preserved. No Android SDK/build/emulator was available in this environment; compile and runtime are NOT verified. Do not replace your stable V9.1 without a build test.
