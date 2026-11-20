# ZomaOS V15.9 integrated repair

- Pearl Day: white band verified in original asset at pixels x=0..11; draw code excludes it by cropping from x=18. No permanent bitmap rewrite.
- Dock: background five-tile art is completely covered in dock area and six visible independent tiles are drawn, with matching `dockEdges` hitboxes and a real Apps action.
- Icon: replaced 96x96 922-byte placeholder with 512x512 image-derived ZO icon.
- Android version: minSdk 16 preserved.
- USB player and existing audio logic untouched.
- Not compiled or run on device; GitHub Actions build and on-device testing required.
