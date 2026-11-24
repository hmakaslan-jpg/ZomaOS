V15.3 Bluetooth Integration — Java compile fix

Fixed both TextUtils.ellipsize calls to use android.text.TextPaint instead of android.graphics.Paint (MainActivity.java).

No Bluetooth, USB, UI or workflow behavior changed. ZIP verified; Android build not executed locally.
