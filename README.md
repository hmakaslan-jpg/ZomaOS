# ZomaOS

A lightweight, themeable home-screen launcher for older Android head units, built and tested on the **2019 Geely X7 (Emgrand X7)**.

> Independent enthusiast project. Not affiliated with or endorsed by Geely, Google, or any vehicle manufacturer. "Geely" and "Emgrand" are trademarks of their owners and appear here only to describe compatibility.

<!-- SCREENSHOTS: replace with real files in docs/screenshots/ -->
| Carbon | Electric | Day |
|---|---|---|
| ![Carbon](docs/screenshots/carbon.jpg) | ![Electric](docs/screenshots/electric.jpg) | ![Day](docs/screenshots/day.jpg) |

<!-- DEMO VIDEO: add a YouTube link or a GIF of the animated night theme + live gauges here -->
**Demo video:** _coming soon_

## Status

**Free preview, v15.8.** Tested by the author on **one** original Geely X7 2019 head unit: 800 x 480 screen, Android 4.x firmware. Other cars, screens, Android versions and firmware builds are **untested**. Do not assume it works on your unit; see [Compatibility](#compatibility) and help us find out.

## Features

- **Three themes:** Carbon (red), Electric (blue), Day (silver)
- **Analog clock** (12 or 24 hour)
- **USB music:** browse folders, play, and resume where you stopped
- **Radio shortcut** to the head unit's built-in radio app
- **Android Auto launch** through the third-party app [OpenAuto by Jan Ried](https://github.com/JanRi3D/OpenAuto/releases) (you install it yourself; ZomaOS only opens it). See [Android Auto setup](#android-auto-setup)
- **App grid** and quick Wi-Fi / Bluetooth settings
- **OBD2 readout** (speed, RPM, coolant, load, fuel) over a Bluetooth ELM327 adapter or a phone bridge, with a **demo mode** that shows simulated data (clearly labelled "DEMO / NOT REAL DATA")
- Designed for **800 x 480 landscape**

ZomaOS does **not** add native Android Auto to hardware that lacks it. It needs a compatible app, phone and USB connection.

## Compatibility

| | |
|---|---|
| Verified | Geely X7 2019, original head unit, 800 x 480, Android 4.x |
| Minimum Android | 4.1 (API 16) |
| Other screens | Untested. Layout is currently fixed to 800 x 480 |
| Other Geely models | Untested. Reports welcome |

Got a different unit? Open a [compatibility report](../../issues/new?template=compatibility-report.md). It takes two minutes and decides what gets supported next.

## Install

**Before you start**

1. Know your head unit's Android version, firmware and screen resolution.
2. Know how to get back to the factory launcher (see [Rollback](#rollback)).
3. Install **only while parked**. Never configure the launcher while driving.
4. Do not root or flash the head unit for this.

**Steps (as done on the tested unit)**

1. Download `ZomaOs.V15.8.Release.apk` from the [latest release](../../releases/latest). Only download from this repository, not from mirrors.
2. Check the file: `sha256sum ZomaOs.V15.8.Release.apk` (Windows: `certutil -hashfile ZomaOs.V15.8.Release.apk SHA256`) and compare with the SHA-256 listed in the release notes.
3. Format a USB flash drive as **FAT32** and copy the APK onto it.
4. On the head unit, set the date to **1 January 2000**.
5. Open the screen that shows the **barcode**.
6. Tap the barcode **7 times** to reveal the hidden File Manager (works on the tested firmware; yours may differ).
7. In File Manager, open the USB drive, select the APK and install it.
8. Press HOME and choose **ZomaOS**. Pick "Always" only after you have tested it. To change it later, open ZomaOS Settings > **Change default launcher**.
9. Test radio, USB music, Bluetooth and steering-wheel buttons before you drive.
10. Set the date back to normal.

If install is blocked, do not bypass security warnings. Report your firmware version in an issue.

Full guide in English, Arabic and Russian: [docs/INSTALLATION_GEELY_X7.md](docs/INSTALLATION_GEELY_X7.md)

## Android Auto setup

ZomaOS does not include Android Auto. The ZomaOS **Android Auto** button opens a separate head-unit app that you install yourself. The author uses **OpenAuto by Jan Ried** (Android package `me.ri3d.openauto`), which supports Android 4.1 and newer.

- Download: [github.com/JanRi3D/OpenAuto/releases](https://github.com/JanRi3D/OpenAuto/releases) (download only from the author's page)
- Tested by the ZomaOS author: **v0.6.0-carplay1** on a Geely X7 2019 (Android 4.1, 800 x 480), Android Auto over a **USB cable**. Wireless and CarPlay are not tested here.
- Install it the same way as ZomaOS (USB drive and the unit's File Manager), then open ZomaOS and tap the Android Auto button.
- Your phone needs the Android Auto app, and the cable must support data.

OpenAuto is a separate project by its own author. ZomaOS is **not affiliated** with it, and does not host or modify it. Please read its page for its terms and for support. It is also not the Raspberry Pi project of the same name by f1x.

## Rollback

If ZomaOS crashes, or HOME stops working:

1. Press HOME, and when asked, choose the factory launcher (do not tick "Always" for ZomaOS until you are sure).
2. Or go to the unit's Settings, Apps, ZomaOS, and **Clear defaults**, then press HOME and pick the factory launcher.
3. Or uninstall ZomaOS from Settings, Apps.

<!-- TODO(author): confirm which of these three works on your unit and delete the rest. -->

## Roadmap

- Scaled layout for other resolutions (1024 x 600 first)
- Compatibility reports from other Geely models
- An animated premium night theme (not announced, no price yet)

## Support the project

ZomaOS is free. If it helps you, a small donation keeps it going: **[ko-fi.com/zomaos](https://ko-fi.com/zomaos)**. Thank you.

## Languages

English is the main language here. Arabic, Russian and other languages are welcome in issues.

## License

Code: MIT. Artwork and theme images: see [LICENSE](LICENSE) and [ASSETS-LICENSE.md](ASSETS-LICENSE.md).
