# V16 repair pass 3 – static interaction fixes

- New themes no longer reuse invisible left sidebar hotspots from original themes.
- New theme music title area uses Play/Resume instead of opening the USB file list.
- Old analog-gauge and album-art touch zones are disabled for new themes.
- Old seek gesture applies only to original themes; avoids intercepting new-theme music panels.
- Dock activates only if finger lifts inside the same tile where it pressed.
- New themes retain a dedicated top-right Settings hotspot.

Validation: ZIP packaging and static source checks only. Gradle build and on-device behavior NOT verified.
