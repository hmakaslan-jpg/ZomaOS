# ZomaOS V15.4 safety repair — verified scope

Implemented: catch USB restore exceptions at launcher startup; catch unreadable USB subdirectories during recursive scans; protect folder listing against inaccessible entries; redraw clock numerals on night themes after masking frozen clock hands.

Existing in source (not hardware-verified): HOME/DEFAULT/LAUNCHER manifest intent filters; Android Auto dock and sidebar call same method.

Not complete: Geely X7 reference-image theme redesign, baked gauges removal, OEM launcher selection, USB music crash root-cause verification, real touch testing, Gradle build. Do not treat as final APK.
