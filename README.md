# ZomaOS V2 — Geely X7 API 16

Experimental **Android 4.1 / API 16** 800x480 landscape dashboard. Build with GitHub Actions (Java 17, Gradle 8.7); Android Studio is not needed on your laptop. This is source, not a compiled APK. Requires network access to Android Gradle Plugin and SDK in GitHub runner.

## Features
- Clearly labeled DEMO simulated data, never silently mixed with live readings.
- ELM327 Bluetooth Classic SPP, select from Android's *already paired* Bluetooth devices.
- Standard read-only OBD mode 01 PIDs: 010D speed, 010C RPM, 0105 coolant, 0104 load, 012F fuel (optional). No ECU writes, no DTC clearing. AT initialization only.
- Optional TCP bridge client: connect to a phone server on `PHONE_IP:8765`. Expected newline-delimited ASCII packets: `SPEED=42;RPM=1700;COOLANT=88;LOAD=30;FUEL=-1` (missing/unsupported values -1, coolant -999). **The phone bridge server is not implemented in this package**. A phone-side companion or compatible server is required before this mode can work.
- OpenAuto launch via installed package `me.ri3d.openauto`; no modification of OpenAuto.
- Trip estimate based on speed/time, resettable; not a vehicle odometer.

## Important limitations
- Head unit Bluetooth may be locked to the factory phone module; paired ELM327 might not be exposed to Android apps. If so, phone bridge is the planned fallback.
- Oppo hotspot IP may change. Use its current IP; 10.126.178.235 was previously observed. Port 8765 is a *proposed* telemetry bridge port, not OpenAuto's 5277.
- No guaranteed OBD PID support on Geely X7; real tests needed.
- The simple ELM response parser is an early prototype and may fail with multiline headers, noisy clones or nonstandard responses. The dashboard intentionally shows `--` on failures.
- Do not use this prototype's data as a driving instrument or safety-critical measurement. Test while parked. Do not select it as default Home app yet.
- On Android 12+ a future phone companion will need modern Bluetooth runtime permissions; this API16 head unit build deliberately uses legacy Bluetooth permissions.

## Upload to existing GitHub repository
Upload **the contents** of this project over the V1 files, preserving `app/` and `.github/workflows/` paths. GitHub may require creating hidden `.github/workflows/build-apk.yml` in the browser manually. Remove/disable V1 workflow if both exist. GitHub Actions should produce artifact `ZomaOS-V2-debug-APK`.


## V2.1 center display options
Tap **CLOCK / VIEW** in the bottom navigation to choose **3D ROAD**, **ANALOG CLOCK**, or **DIGITAL CLOCK**. Choice persists across app restarts. Clock uses the head unit system time, and works offline without OBD. Analog clock hands include seconds. OBD telemetry and OpenAuto launch remain available.

**Build:** Push the project files preserving paths including `.github/workflows/build-apk.yml`. GitHub Actions builds a debug APK. V2.1 is not yet device-tested; test install/launch before relying on it.
