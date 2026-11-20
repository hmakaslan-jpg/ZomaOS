# Build Fix Notes

Root cause: `master_carbon.png` + `master_carbon.jpg` (and electric/pearl) coexist in `drawable-nodpi` on GitHub. Android identifies drawable resources by basename, causing `mergeDebugResources` to fail.

Fix: added an automatic cleanup step to `.github/workflows/build-apk.yml` before `assembleDebug`. The step deletes only the 3 old PNGs from the runner's temporary checkout and validates unique image names. Existing optimized JPGs remain unchanged. This fixes the reported duplicate-resource error; other build/runtime problems are not ruled out.

Recommended later: delete the three old PNGs from Git history's current tree for permanent repository hygiene. This is not necessary for the workflow to build.
