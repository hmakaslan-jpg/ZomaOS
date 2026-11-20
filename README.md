# ZomaOS NEXUS V15.1 — Clean Source

Source-only package for the Geely X7 legacy Android head unit.

## Build
GitHub Actions: push source to main and run the `Build ZomaOS V15.1 Clean` workflow.
Debug APK will be uploaded as an Actions artifact. This package does not include a built or device-tested APK.

## Cleanup
Removed historical reports and version notes from the distribution ZIP. Kept all app source files and image resources to avoid accidental regressions in USB Music, radio, Android Auto and theme selection. Updated the outdated GitHub Actions APK artifact name.

## Important
The visual master backgrounds are beta artwork; build and runtime testing are still required.
