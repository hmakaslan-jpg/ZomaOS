# Public release readiness — 22 November 2026

## Inspected build
- Approved device-tested source commit: `a42108814dec0cdad06dee0b50e068f297e6f4e6`.
- GitHub Actions run: `44938404661`, artifact: `14720796103`.
- ZIP downloaded and inspected. Contains only `app-debug.apk` (362,452 bytes).
- APK SHA-256: `debb87d642d3c3d0e8f768df26074fe0962d9b3fcff48958b8a4f4dbbded127e`.
- Java `jarsigner -verify -verbose -certs` identifies `CN=Android Debug` with `SHA1withRSA` and warns the JAR is treated as unsigned under current Java security policy because SHA-1 signatures are disabled. This does **not** establish that installation on the legacy target unit fails, but it is **not acceptable to present as a verified public release**.

## Release blockers
1. Build a properly release-signed APK with a persistent, securely held release key, preserving compatibility with the tested legacy Android version.
2. Validate APK signature using Android build tools (e.g. `apksigner verify --verbose --print-certs`) and validate package metadata.
3. Test release-signed APK on the actual Geely X7 unit, including install, upgrade behavior, launcher selection, radio, USB, themes, and rollback.
4. Confirm redistribution rights for images and icons.
5. Publish a permanent GitHub Release with version, checksum, compatibility caveats, and installation guide. Only then publish download links to public communities.

## Marketing rollout
- Facebook brand page: https://www.facebook.com/profile.php?id=61596464927149
- Prepare one post with **real** Carbon/Electric/Day screenshots and a link to the public release once available.
- Community posts: Arabic for Egypt; Russian for compatible X7 communities; English for broader international testers. Check each group's rules before posting. No unsolicited posting or misleading compatibility claims.
- First 20 testers: collect head-unit model, firmware, Android version, resolution, installation result, and button/clock/theme behavior.

**Status:** Preparing release, **not yet published**. Do not post the Actions artifact or debug APK as the public download.
