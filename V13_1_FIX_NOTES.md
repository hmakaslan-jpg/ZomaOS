# V13.1 corrective checkpoint

- Removed stretched `nexus_glass_panel.png` drawing from `liveCard()` because its decorative circular strokes were stretched over every rectangular card and created the giant white arcs shown in the user screenshot.
- Restored responsive rounded rectangle painting with gradient, subtle specular edge, drop shadow and bottom glow, using each card's own geometry.
- Kept existing touch hitboxes, USB resume and media control methods untouched.
- No APK build, Android emulator execution, or physical head-unit verification has been performed.
- This is a targeted bugfix, NOT a finished visual match to the NEXUS reference.
