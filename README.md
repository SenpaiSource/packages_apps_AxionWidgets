# AxionWidgets

This repository provides authentic widgets adapted for AOSP-based ROMs.

## Features
- **Universal Launcher Compatibility**: Fully compatible with Launcher3, AOSP-based launchers, and third-party launchers (Nova Launcher, Lawnchair, etc.) with **zero launcher repo forks or modifications required**.
- **16 Authentic Dot-Matrix Widgets**:
  - **Digital Clock**: Dot-matrix font (`ndot57`) with configurable backgrounds (solid / transparent).
  - **Analog Clock**: Minimalist analog dial.
  - **World Clock**: Multiple timezones in authentic dot-matrix typography.
  - **2x2 Battery Ring**: Circular dial with battery percentage, charging animations, and connected Bluetooth device levels.
  - **Screen Time**: Authentic Nothing smiling/frowning dot-matrix face displaying daily screen-on time.
  - **Media Player**: Track name, artist, progress, and playback controls.
  - **Pedometer**: Step counter with daily goal tracking.
  - **Compass**: Authentic real-time orientation dial using `SensorManager`.
  - **QuickLook**: At-a-Glance widget showing weather, date, time, and calendar appointments.
  - **Photo Widget**: Frame favorite pictures in Dot-matrix aesthetic.
  - **Countdown & Year Progress**: Event countdown and yearly progress percentages.
  - **Fidget Toys**: Bottle Spinner and Rock-Paper-Scissors interactive widgets.
  - **Quick Settings Tiles**: Fast toggles on the home screen.
- **Embedded Authentic Typography**: Bundles genuine `ndot57` and `ndot_57_aligned` fonts inside `res/font/`, ensuring dot-matrix rendering across all ROMs without requiring system font patches.
- **Universal Platform APIs**: Decoupled from proprietary ROM system daemons; battery monitoring uses standard Android `BatteryManager`, and orientation uses `SensorManager`.
- **Weather Integration**: Includes `NothingWeather` prebuilt app with full forecast UI.

## Credits & Upstream
- **AxionAOSP Project**: The core widget implementations and dot-matrix layouts are based on [android_packages_apps_AxionWidgets](https://github.com/AxionAOSP/android_packages_apps_AxionWidgets).
  - `rmp22 <195054967+rmp22@users.noreply.github.com>`
  - `Saikrishna1504 <saikrishna26918@gmail.com>`
  - `Rve27 <rve27github@gmail.com>`

## Inclusion in ROM

Clone this repository into `packages/apps/AxionWidgets`:

```xml
<project path="packages/apps/AxionWidgets" name="SenpaiSource/android_packages_apps_AxionWidgets" remote="github" revision="16" />
```

Then inherit the product makefile in your device or common makefile (e.g. `device/oneplus/sm8650-common/common.mk`):

```makefile
$(call inherit-product-if-exists, packages/apps/AxionWidgets/AxionWidgets.mk)
```
