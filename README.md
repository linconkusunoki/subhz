<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="SubHz app icon" />

# SubHz

**A native Android music client for your own Subsonic-compatible server.**

Stream the library you host yourself — Navidrome, Airsonic, Gonic, Symfonium's server, anything
speaking the Subsonic API. No account, no cloud, no closed format.

[![Latest release](https://img.shields.io/github/v/release/linconkusunoki/substream?logo=android&logoColor=white&label=Latest)](https://github.com/linconkusunoki/substream/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/ExoPlayer-Media3%201.11-FF6D00?logo=googleplay&logoColor=white)](https://developer.android.com/media/media3)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

### [Download the APK](https://github.com/linconkusunoki/substream/releases/latest/download/SubHz.apk) · [All releases](https://github.com/linconkusunoki/substream/releases)

</div>

---

## Download

Grab the latest signed APK and install it on your phone:

**[Download SubHz.apk (latest release)](https://github.com/linconkusunoki/substream/releases/latest/download/SubHz.apk)**

- Requires **Android 7.0 (API 24)** or newer.
- Because SubHz is self-published (not on Google Play), your device will ask you to allow
  installing apps from **unknown sources** — that's expected for any sideloaded app. Google Play
  Protect may also warn that the publisher is unknown; choose **Install anyway**.
- Every release is signed with the same key, so updates install cleanly over the top — no need to
  uninstall first.

Prefer to build it yourself? See [Getting started](#getting-started).

## Why

Self-hosted music deserves a first-class phone app. SubHz is a single-module, fully-Compose
Android app that talks straight to your server over the Subsonic REST API — no intermediate
service, no sync proxy, nothing of yours stored outside the device.

## Features

- **Home** — a shelf-style layout: quick picks, recently added, frequently played, made-for-you
  (random), favorites, and your artists. Everything hidden that has no cover art.
- **Library** — albums, artists (alphabetical index, main artists only), playlists, favorites.
- **Search** — artists, albums, and tracks in one query (`search3`).
- **Now Playing** — swipeable full-screen sheet with queue, shuffle, and repeat
  (off → all → one).
- **Background playback** — a Media3 `MediaSessionService` keeps audio alive outside the app with
  a real notification, lock-screen art, and hardware button support.
- **Favorites everywhere** — star/unstar albums and artists, toggled straight from the UI and
  optimistic on screen.
- **Album & artist detail** — tracklists, play-anything, per-artist song lookup.
- **Dynamic server login** — point the app at any Subsonic host, username, and password. Swapping
  servers clears cached art URLs and stops playback so credentials never leak across hosts.
- **Material 3** — light/dark, edge-to-edge, adaptive bottom nav, extended material icons.

## Tech stack

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose + Material 3 + Adaptive Navigation Suite |
| Architecture | MVVM with `ViewModel` + `StateFlow`, sealed UI states |
| DI | Koin |
| Networking | Retrofit 3 + OkHttp 5 + kotlinx.serialization |
| Playback | AndroidX Media3 (ExoPlayer + MediaSessionService) |
| Images | Coil |
| Navigation | Navigation Compose |
| Min SDK | 24 (Android 7.0) · Target/Compile SDK 37 |
| Build | Gradle 9.6 · AGP 9.4.1 · Kotlin 2.2.10 · JDK 11 bytecode |

## Getting started

**Requirements:** Android Studio (or a JDK 17+ toolchain), and a reachable Subsonic server.

```bash
git clone https://github.com/linconkusunoki/substream.git
cd substream
./gradlew installDebug
```

Or open the project in Android Studio and hit ▶. On first launch the app asks for a **server URL**
(`music.example.com`), **username**, and **password** — credentials are salted and MD5-hashed per
the Subsonic token scheme, never stored in plaintext. Settings lets you re-test the connection,
switch servers, or log out.

To build a release APK locally:

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk (unsigned unless signing env vars are set)
```

## How releases are built

Releases are produced by GitHub Actions ([`.github/workflows/release.yml`](.github/workflows/release.yml)).
Pushing a tag like `v1.0.0` — or clicking **Run workflow** on the *Release* action — builds a
**signed** release APK and attaches it to a GitHub Release. The download link at the top of this
README always points at the newest one.

The signing key lives only in the repository's **Actions secrets** (`KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and is injected at build time — it never touches
the source tree. With no secrets configured, the workflow still builds, but the APK is left
unsigned.

<details>
<summary><strong>One-time signing setup (maintainers)</strong></summary>

1. Create a keystore (keep it safe — losing it means users must uninstall before updating):

   ```bash
   keytool -genkeypair -v \
     -keystore substream-release.jks -alias substream \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -storepass YOUR_STORE_PASS -keypass YOUR_KEY_PASS \
     -dname "CN=SubHz, OU=Dev, O=SubHz, C=US"
   ```

2. Add the four secrets to the repo (Settings → Secrets and variables → Actions), base64-encoding
   the keystore:

   ```bash
   # macOS
   base64 -i substream-release.jks | pbcopy
   # Linux
   base64 -w0 substream-release.jks   # copy the output
   ```

   | Secret | Value |
   | --- | --- |
   | `KEYSTORE_BASE64` | the base64 string from above |
   | `KEYSTORE_PASSWORD` | `YOUR_STORE_PASS` |
   | `KEY_ALIAS` | `substream` |
   | `KEY_PASSWORD` | `YOUR_KEY_PASS` |

3. Tag a release: `git tag v1.0.0 && git push origin v1.0.0`.

</details>

## Project structure

```
app/src/main/java/com/subhz/
├── MainActivity.kt, SubHzApp.kt
├── data/
│   ├── api/         Subsonic REST models, Retrofit service, auth + interceptors
│   ├── preferences/ Server config and credential storage
│   └── repository/  SubsonicRepository — single source of truth for the API
├── di/              Koin module
├── player/          PlaybackService (MediaSessionService), PlayerManager, cache
└── ui/
    ├── components/  PlayerBar
    ├── navigation/  Routes and MainScreen scaffold
    ├── screens/     Login, Home, Library, Search, Settings, Album/Artist detail, Now Playing
    └── theme/       Material 3 color, type, theme
```

Every call funnels through `SubsonicRepository`, which returns `Result<T>` — screens never touch
Retrofit or OkHttp directly.

## Tests

```bash
./gradlew test
```

Unit tests cover URL/auth construction, server config parsing, main-artist filtering, and home shelf
composition; Compose UI tests cover the home screen states.

## License

[MIT](LICENSE) © 2026 Lincon Kusunoki
