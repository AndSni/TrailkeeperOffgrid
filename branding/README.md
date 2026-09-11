# Branding sources

The raw source art. Generated Android resources are derived from these —
regenerate with the recipes below if the art changes.

| File | Used for |
|------|----------|
| `appicon.png` | 512×512, opaque black background + white line art. Source for the adaptive launcher icon. |
| `splashscreen.png` | 1100×1440, transparent background + white line art (skull emblem, pine trees, crossed tools, "TRAILKEEPER OFFGRID" wordmark). Source for the launch splash. |

## App icon

`appicon.png` is grayscale (R=G=B everywhere) white-on-black, drawn at
512×512 **already scaled and padded to Android's adaptive-icon keyline
template** (glyph inscribed in the template's inner safe circle, so it
survives every launcher mask). The foreground is built with a
luminance-as-alpha cutout (white → opaque, black → transparent) and a
straight resize of the *whole* 512×512 canvas to each density — no
re-cropping and no re-scaling to some other fraction, since that would
throw away the padding already calibrated against the template and
zoom the glyph in past the safe zone (this happened once — v0.2.2 shipped
an over-cropped, over-scaled icon; fixed same day):

```python
from PIL import Image
import numpy as np

im = Image.open("appicon.png").convert("RGBA")
lum = np.array(im)[:, :, 0]  # R channel; image is grayscale
cutout = np.zeros((*lum.shape, 4), dtype=np.uint8)
cutout[:, :, :3] = 255
cutout[:, :, 3] = lum
logo = Image.fromarray(cutout, "RGBA")  # full 512x512 canvas, padding as authored

for folder, px in {
    "mipmap-mdpi": 108, "mipmap-hdpi": 162, "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324, "mipmap-xxxhdpi": 432,
}.items():
    logo.resize((px, px), Image.LANCZOS).save(
        f"android/app/src/main/res/{folder}/ic_launcher_foreground.png"
    )
```

`android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` pairs that
foreground with a solid `#000000` background (`values/colors.xml`
`ic_launcher_background`) and reuses it as the `monochrome` variant (its
alpha channel is already a clean mask of just the line art, so Android's
themed-icon tinting looks right). No legacy per-density PNG mipmaps are
needed — `minSdk 26` is exactly when adaptive icons shipped.

## Splash screen

`splashscreen.png` is copied as-is to
`android/app/src/main/res/drawable-nodpi/splash_logo.png` (`nodpi` — a
single fixed asset, not density-scaled) and shown full-composition via a
plain Compose `Image(contentScale = ContentScale.Fit)` in
`TrailkeeperApp.kt`'s `SplashScreen()`, on the same dark background
(`@color/splash_background`, `#141712`, matching `ui/theme/Theme.kt`) that
`android:windowBackground` already uses — so there's no flash before it
draws. Deliberately **not** the `androidx.core:core-splashscreen` /
Android 12 `SplashScreen` API: that API constrains you to a small
(~240dp) centered icon and doesn't fit a full poster-style composition
like this one.
