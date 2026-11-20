# ZomaOS clock-mode implementation — phase 1

Implemented: a global Settings option **Clock face • Analog / OBD speed** which saves its selection in SharedPreferences and works with all three existing themes. The earlier Carbon-only prompt is removed. Speedometer shows `--` without live OBD data. Existing USB music and dock logic are unchanged.

Not yet implemented: three new fully functional themes, their correct 2019 Emgrand X7 car imagery, or switching the *new* themes between clock and gauge. The generated visual concepts are not embedded as functional UI; they contain baked/static values and some incorrect vehicle models. No APK build or device test was performed.
