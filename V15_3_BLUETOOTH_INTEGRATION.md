# V15.3 Bluetooth OEM Integration

- Music hub now includes **Bluetooth Music – factory player** and **Bluetooth / Phone settings**.
- Left-side Phone icon opens user-selected factory Phone/Dial application.
- First launch asks the user to select the OEM app from installed launcher activities; the explicit component is saved. This avoids guessing a package name.
- Change app selection from the Bluetooth / Phone settings menu.
- No direct AVRCP metadata, album art, call handling, or in-launcher Bluetooth controls are claimed; OEM stack access must be separately established.
- USB and existing music playback methods unchanged.
- Source-level verification only: no Android runtime or Gradle build executed.
