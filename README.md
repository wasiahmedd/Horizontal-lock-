# Horizon Lock – Camera Stabilization App for Samsung Galaxy A34

A native Android camera app that brings **Horizon Lock** (Horizontal Lock) video stabilization to phones that don't have it built in — including the Samsung Galaxy A34.

---

## What It Does

| Feature | Details |
|---|---|
| **Horizon Lock** | Counter-rotates the camera preview in real-time to keep the horizon perfectly level |
| **Sensor Fusion** | Uses `TYPE_ROTATION_VECTOR` (gyroscope + accelerometer) for drift-free roll detection |
| **HD Video Recording** | Saves to `Movies/HorizontalLock/` in your gallery |
| **Lock Toggle** | Tap the lock icon to enable/disable stabilization on the fly |
| **Immersive UI** | Full-screen dark theme, real-time tilt angle readout |

---

## Screenshots

> Coming soon – install the APK and try it on your device!

---

## Download APK (from GitHub Actions)

1. Go to the [**Actions** tab](../../actions) of this repository
2. Click the latest **Build APK** workflow run
3. Scroll to **Artifacts** → download `HorizonLock-debug-APK.zip`
4. Unzip and transfer the `.apk` to your phone
5. Enable **Install unknown apps** in your phone settings and install

---

## How It Works

```
Phone tilts left/right
        ↓
SensorManager TYPE_ROTATION_VECTOR
        ↓
Compute roll angle (radians → degrees)
        ↓
Low-pass filter (α=0.85) for smooth output
        ↓
Counter-rotate PreviewView by -roll°
Scale up by |cosθ| + |sinθ| to fill frame
        ↓
Recorded video stays level ✓
```

---

## Build Locally

### Prerequisites
- Java 17+
- [Gradle 8.7](https://gradle.org/install/)
- Android SDK (API 26–34)

### Steps

```bash
# Clone
git clone https://github.com/wasiahmedd/Horizontal-lock-.git
cd Horizontal-lock-

# (First time only) Generate Gradle wrapper
gradle wrapper --gradle-version 8.7

# Build debug APK
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## Sideload to Phone

```bash
# Via ADB (with Developer Options enabled)
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or transfer the APK file over USB / WhatsApp / email and open it on your phone.

---

## Permissions Required

| Permission | Reason |
|---|---|
| `CAMERA` | Camera preview and recording |
| `RECORD_AUDIO` | Audio in video recordings |

---

## Compatibility

| Device | Status |
|---|---|
| Samsung Galaxy A34 | ✅ Tested target |
| Any Android 8.0+ (API 26+) with gyroscope | ✅ Should work |

---

## Tech Stack

- **Kotlin**
- **CameraX** (`1.3.3`) — Preview + VideoCapture
- **Android Sensor API** — `TYPE_ROTATION_VECTOR`
- **ViewBinding**
- **Kotlin Coroutines + StateFlow**
- **GitHub Actions** — CI/CD APK build

---

## License

MIT License — free to use, modify, and distribute.
