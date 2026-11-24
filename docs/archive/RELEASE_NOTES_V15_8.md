# ZomaOS Free V15.8 — Candidate release notes

**Status: Release candidate documentation; NOT a published GitHub Release.**

Source revision tested on the developer's head unit: `a42108814dec0cdad06dee0b50e068f297e6f4e6`.

GitHub Actions run: https://github.com/hmakaslan-jpg/ZomaOS/actions/runs/44938404661

Build artifact ID: `14720796103` (temporary Actions artifact; **not a permanent release asset**).

## Highlights

- Three visual themes: Carbon, Electric and Day
- Analog clock and launcher shortcuts
- USB music playback and resume behavior on the developer's device
- Radio and Android Auto shortcut integrations (subject to installed applications and device support)

## Verified scope

Developer's original Geely X7 2019 head unit, 800×480, legacy Android 4.x. Other devices are **unverified**.

## Known limitations

- Day theme's Apps button can have slight residual background shading.
- Android Auto requires compatible third-party software and phone.
- No vehicle speedometer or CAN-bus data integration.
- Other firmware, resolutions and vehicle variants are untested.

## Publication gates

1. Check that all background images, generated effects, icons and other assets can be redistributed.
2. Retrieve the exact APK from the above successful run; verify its package name, installability, signature and SHA-256.
3. Test clean installation, upgrade, app functions and return to stock launcher while parked.
4. Attach the verified APK and SHA-256 to a new **GitHub Release**.
5. Replace temporary Actions links in public posts with the stable Release URL.

**Do not redistribute a different branch HEAD build as this approved candidate without repeating the device tests.**
