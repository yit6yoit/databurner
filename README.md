# Data Burner (Android)

A minimal Android app that **downloads as much data as possible, as fast as
possible**, from public speed-test servers over many parallel connections, and
**throws the bytes away** so it never fills up storage. Useful for deliberately
consuming data (e.g. testing an "unlimited" plan) or stress-testing a link.

## What it does

- Opens N parallel HTTP connections (default 16, adjustable 1–64).
- Each connection streams a large body from a public test server, reads it into
  a throwaway buffer, counts the bytes, and re-requests when the body ends — so
  the pipe stays saturated indefinitely.
- Runs as a **foreground service** with a **partial wake lock**, so it keeps
  downloading with the screen off / app in the background.
- Live UI: current speed (Mbps), session total downloaded, active connections,
  elapsed time, Start / Stop.
- **All-time used** counter that persists across runs and restarts
  (stored on-device). Long-press the all-time card to reset it to zero.

### Servers used
Defined in `app/src/main/java/com/example/databurner/TestServers.kt`:

- Cloudflare — `speed.cloudflare.com/__down?bytes=…` (1 GiB per request)
- Hetzner — `speed.hetzner.de` / `ash-speed.hetzner.com`
- ThinkBroadband — `ipv4.download.thinkbroadband.com` (HTTP; cleartext is
  allowed via `res/xml/network_security_config.xml`)

These are public endpoints intended for bandwidth testing. **Only point the app
at servers that are meant to be load-tested.** Edit `TestServers.kt` to change
the list.

## Requirements
- Android Studio (Koala / 2024.1 or newer recommended)
- Android SDK: compileSdk 34
- Min Android version: **8.0 (API 26)**
- JDK 17 (bundled with recent Android Studio)

## Build & run

### Easiest — Android Studio
1. **File → Open** and select this `DataBurner` folder.
2. Let it sync (it downloads the Android Gradle Plugin and dependencies the
   first time — needs internet).
3. Plug in a phone (USB debugging on) or start an emulator, then press **Run**.

### From a phone only — no PC (GitHub Actions cloud build)
Android Studio can't run on a phone, so build in the cloud instead:
1. Put this project in a GitHub repo (upload the folder via github.com in a
   mobile browser, or the GitHub mobile app, or `git push`).
2. The included workflow `.github/workflows/android.yml` runs automatically on
   every push — or open the **Actions** tab and tap **Build APK → Run workflow**.
3. When the run finishes (~3–5 min), open it and download the
   **DataBurner-debug-apk** artifact (a zip containing `app-debug.apk`).
4. Unzip on the phone, tap the APK, allow "install unknown apps", install.

> On-device compilation (e.g. Termux + command-line SDK + Gradle) is possible
> but slow and fiddly; the cloud build above is the recommended phone-only path.

### Command line (on a computer)
```bash
# first time on macOS/Linux, make the wrapper executable:
chmod +x gradlew

# tell Gradle where your Android SDK is (or set ANDROID_HOME / use Studio):
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# build a debug APK:
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug

# install onto a connected device:
./gradlew installDebug
```
The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

> This project was generated in an offline sandbox, so the APK was **not**
> pre-built — the Android SDK and Gradle/Maven repositories weren't reachable
> there. The first build on your machine will fetch everything automatically.

## Notes & cautions
- This can burn through **a lot** of mobile data very quickly. On a metered /
  capped plan it may cost real money. Use on Wi-Fi or an unlimited plan unless
  that's exactly the point.
- Battery/heat: parallel downloads + wake lock are intensive. The service caps
  the wake lock at 12h as a safety net.
- Bytes are never written to disk, so storage is never consumed.

## Project layout
```
app/src/main/java/com/example/databurner/
  MainActivity.kt      UI, live speed sampling, start/stop, all-time display
  DownloadService.kt   foreground service, parallel download workers, wake lock
  TestServers.kt       list of public test endpoints
  BurnStats.kt         shared session counters (bytes, connections, running)
  LifetimeStats.kt     persistent all-time total (SharedPreferences)
.github/workflows/
  android.yml          cloud build — produces app-debug.apk as an artifact
app/src/main/res/
  layout/activity_main.xml
  values/…             strings, colors, theme
  xml/network_security_config.xml   allows HTTP for thinkbroadband
```
