# Nothing OS Authentic Widgets Port for AOSP / Custom ROMs

This repository provides authentic Nothing OS Widgets (`NothingCardService`, `NothingCardLab`, and `NothingWeather`) adapted for non-Nothing devices.

## Features
- **Authentic Nothing Widgets**: Clocks (Analog, Digital, World), Battery (Big & Small), Compass, Pedometer, Screen Time, Countdown, Media Player, Quick Look, Photo widgets, Community, ChatGPT, News, and Weather (Circle, Square, Rectangle, Combo, Sunset, Air Quality).
- **Glyph Components Neutralized**: All Glyph Matrix services (`GlyphMatrixClockService`, `GlyphMatrixBottleService`, `GlyphMatrixBatteryService`, `GlyphMatrixFingerGussService`, `GlyphMatrixSolarService`, `GlyphMatrixStopwatchService`, `GlyphMatrixBreathService`) are disabled via Android `component-override` to avoid background crashes and wake locks.
- **Self-Contained Stub**: Includes `com.nothing.experience.AppTracking` stub inside APK multidex to prevent `ClassNotFoundException` crashes.
- **Platform Certificate Integration**: Signs with ROM `platform` certificate via Soong, granting all required signature-level permissions across modules.
- **Universal Weather Bridge Ready**: Exposes `com.nothing.weather.share` with `query_weather_info` and `query_weather_temp_unit` for interoperability with AOSP and third-party weather providers.

## Inclusion in ROM

Add this single line to your device or common makefile (e.g. `device/oneplus/sm8650-common/common.mk`):

```makefile
$(call inherit-product-if-exists, vendor/nothing/widgets/widgets.mk)
```
