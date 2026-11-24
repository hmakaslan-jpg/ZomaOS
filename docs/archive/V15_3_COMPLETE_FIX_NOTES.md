# V15.3 Complete Fix - source candidate

- Correct dock hit regions using the exact five drawn button boundaries, not a uniform 146px grid.
- Seek touch region aligned with the displayed progress bar.
- Master themes retain embedded art; music title, artist, cover, position and controls are drawn live in an opaque panel.
- Daylight media panel contrast and border improved.
- Live header time/version replaces stale text in the top-right area.
- Disabled duplicate clock hands because the master images still contain baked hands. This is a temporary visual safety measure, NOT a functioning analog clock fix.
- GitHub workflow removes obsolete image resources and old master PNG duplicates.

Not verified: Android build, real device touch/USB/Radio/AA operation. The master clock backgrounds still require true clean art without baked hands, and daylight art still needs full visual redesign.
