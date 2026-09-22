# Wireless Smart Display 🌙🔋

An OLED Smart Display screen saver and nightstand ambient clock for Android, optimized for wireless charging docks, stands, and bedside use.

Built with **Modern Android (Kotlin & Jetpack Compose)** with zero wake-lock battery waste, active OLED pixel protection, and dark-adapted bedside features.

---

## Features

### 🌙 OLED & Bedside Night Mode
* **Ultra-Dim Night Clock**: Pure black background (`#000000`) for OLED displays, turning off pixels completely to prevent room glare.
* **Custom Night Color Palette**: Choose your bedtime display tone:
  * 🔴 **Red** (Default, preserves dark-adapted night vision)
  * 🟠 **Amber** (Warm incandescent glow)
  * 🔶 **Deep Orange** (Rich, soothing tone)
  * 🟢 **Soft Green** (Low-energy eye sensitivity)
  * ⚪ **Warm White** (Muted candle-light tone)
* **Extra-Large Centered Brightness HUD**: A 36sp bold percentage HUD pops up in the middle of the screen when swiping vertically, easily legible without glasses.
* **Midnight Path Floodlight ("Bathroom & Water Trip" Mode)**:
  * **Long-press anywhere** on the night screen for ~0.5s to turn the entire display into a diffused night-vision lantern.
  * Illuminates hallways softly without harsh blue light or waking partners.
  * Tap anywhere to instantly dismiss.
  * Built-in **5-minute safety auto-shutoff** in case you fall back asleep.
* **Tap Screen to Dim**: Single tap toggles between full-color dashboard and ultra-dim clock.
* **Anti-Burn-In Pixel Drift**: Periodically shifts UI elements by subtle pixel offsets every 60 seconds to protect OLED screens.

### 🔕 Automatic Bedside Peace & Privacy
* **Priority Do Not Disturb (DND)**: Automatically engages Priority DND during night mode (via ambient light sensor or clock mode) and restores prior state on wake. Leaves pre-existing DND untouched.
* **Mute Location Dot**: Temporarily mutes background location requests during night mode to eliminate Android's flashing green/blue privacy dot in dark bedrooms.

### 📐 Charging & Stand Awareness
* **Require Propped Up**: Uses phone incline sensors to only stay on when resting upright on a charging stand or dock; automatically turns off display if laid flat on the mattress.
* **Wireless Charging Only Mode**: Option to only run when charging on a wireless dock, immediately dismissing if connected via standard USB-C cable.

### 📱 Modular Dashboard Widgets (Full-Color Mode)
* **Clock & Date**: Digital clock with optional seconds display.
* **Dual Clock Portrait View**: Large digital clock on top half, sweeping analog clock on bottom half.
* **Battery & Dock Stats**: Real-time battery percentage, charging speed, and wattage.
* **Next Alarm**: Displays upcoming morning alarm with 1-tap dismiss.
* **Weather Forecast**: Live temperature and condition updates via Open-Meteo.
* **Thermostat & Climate Card**: Current & target temperatures with HVAC mode indicators.
* **Brown Noise Generator**: Integrated sound machine playing continuous, soothing brown noise for sleep.
* **Smart Home Shortcuts**: Quick launches for Google Home and Alexa.

---

## Tech Stack
* **Language**: Kotlin 1.9+
* **UI**: Jetpack Compose (Material 3)
* **Architecture**: Flow, Coroutines, StateFlow
* **Platform**: Android 8.0+ (API level 26+)
* **Build System**: Gradle 8.10+ (Kotlin DSL)

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/<your-username>/WirelessSmartDisplay.git
cd WirelessSmartDisplay

# Build debug APK
./gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## License
Apache 2.0 or MIT (Open Source)
