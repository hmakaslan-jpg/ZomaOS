# ZomaOS V15.2 Lite Premium — Build Fix

This package fixes the GitHub Actions `:app:mergeDebugResources` failure caused by old `master_carbon.png`, `master_electric.png`, and `master_pearl.png` files remaining in the repository alongside the optimized `.jpg` versions.

## Upload to GitHub

1. Extract this ZIP.
2. Upload **all extracted files and folders**, keeping their paths, to the repository root on `main` (including `.github/workflows/build-apk.yml`). GitHub upload does **not** automatically delete older files.
3. Commit changes and run **Actions > Build ZomaOS V15.2 Lite Premium > Run workflow**.

The workflow now automatically removes those 3 obsolete PNGs from the build checkout before Gradle runs, even if the old PNG files still exist in GitHub. It also checks for any remaining duplicate image resource names.

The ZIP is source code, not a compiled APK. No Android build or device runtime test has been performed here.
