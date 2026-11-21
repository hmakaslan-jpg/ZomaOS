# V15.8 Touch Repair - 2026-11-21

Changes:
- Five dock hitboxes aligned to the original 800x480 artwork (x: 70,214,356,498,640,787; y:382..472).
- A small finger drift (>18 px) cancels ONLY the pending long-press, not the tap.
- ACTION_UP activates the original tile only if released in/near that same tile (32 px tolerance); drags far away do not activate.
- ACTION_CANCEL clears state; long-press callback removed on release.
- Existing Android 4.1 navigation-bar handling retained.

Verification: ZIP integrity and static source assertions. Android build and hardware touch test NOT performed. The root cause of the two-tap issue remains unconfirmed until on-device testing.
