# Velo Browser

A lightweight, fast, production-ready Android browser inspired by Via Browser — but more advanced.

## Features

- **WebView Engine** — native Android WebView, zero Chromium overhead
- **Multi-Profile System** — open the same site with different accounts via isolated cookie sessions
- **Ad Blocker** — EasyList-format filter rules, intercepts at `shouldInterceptRequest`
- **Multi-Tab Management** — tabs with incognito mode, tab switcher
- **Full Settings System** — DataStore-backed, instant persistence
- **Multilingual** — English + French, auto-detects device language, supports manual switch
- **Download Manager** — system-level downloads with notifications
- **Ultra Fast Mode** — disables images and JavaScript for speed
- **Privacy Mode** — incognito tabs with no history/cookie persistence
- **Safe Browsing** — Google SafeBrowsing API, SSL error rejection

## Architecture

```
Clean Architecture (3 layers)
├── UI Layer        → Activities, ViewModels, Adapters (ViewBinding)
├── Domain Layer    → Use Cases, Repository Interfaces, Domain Models
└── Data Layer      → Room DB, DataStore, Repository Implementations
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0 |
| UI | ViewBinding + Material Design 3 |
| Async | Kotlin Coroutines + Flow |
| DI | Hilt |
| Database | Room |
| Settings | DataStore Preferences |
| Browser Engine | Android WebView |

## Build & Run

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK API 24+

### Local Build

```bash
# Clone
git clone https://github.com/YOUR_USERNAME/velo-browser.git
cd velo-browser

# Copy local.properties.example → local.properties
cp local.properties.example local.properties
# Edit local.properties and set your SDK path

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### CI/CD via GitHub Actions

Push to `main` → GitHub Actions automatically:
1. Builds Debug and Release (unsigned) APKs
2. Runs lint checks
3. Uploads APKs as artifacts
4. Creates a GitHub Release with the debug APK

**Artifacts download**: Go to `Actions` → Select a run → Download `velo-browser-debug-*`

### Signed Release via GitHub Actions

1. Generate a keystore: `keytool -genkey -v -keystore velo_release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias velo`
2. Base64-encode it: `base64 -i velo_release.jks | pbcopy`
3. Add GitHub repo secrets:
   - `KEYSTORE_BASE64` — the base64 keystore
   - `KEYSTORE_PASSWORD` — keystore password
   - `KEY_ALIAS` — key alias (`velo`)
   - `KEY_PASSWORD` — key password
4. Push a tag: `git tag v1.0.0 && git push origin v1.0.0`

## Project Structure

```
app/src/main/
├── java/com/velobrowser/
│   ├── VeloApplication.kt          ← Hilt app entry point
│   ├── ui/
│   │   ├── browser/                ← BrowserActivity, BrowserViewModel
│   │   ├── tabs/                   ← TabsBottomSheet, TabsAdapter
│   │   ├── settings/               ← SettingsActivity, SettingsFragment
│   │   ├── profiles/               ← ProfileManagerActivity + ViewModel
│   │   └── downloads/              ← DownloadsActivity
│   ├── core/
│   │   ├── browser/                ← WebView clients, WebViewFactory
│   │   ├── tabs/                   ← TabManager (in-memory state)
│   │   ├── adblocker/              ← AdBlocker, FilterListParser
│   │   └── download/               ← VeloDownloadManager, Notifications
│   ├── domain/
│   │   ├── model/                  ← BrowserTab, Profile, HistoryEntry…
│   │   ├── repository/             ← Repository interfaces
│   │   └── usecase/                ← Business logic use cases
│   ├── data/
│   │   ├── local/db/               ← Room Database, DAOs, Entities
│   │   ├── local/datastore/        ← SettingsDataStore
│   │   └── repository/             ← Repository implementations
│   ├── di/                         ← Hilt AppModule
│   └── utils/                      ← UrlUtils, LocaleUtils, Extensions
├── res/
│   ├── values/strings.xml          ← English strings
│   ├── values-fr/strings.xml       ← French strings
│   ├── raw/adblock_rules.txt       ← Bundled ad block filter list
│   └── xml/network_security_config.xml ← HTTPS enforcement
└── assets/
    └── adblock_rules.txt           ← EasyList-format rules
```

## Adding More Languages

1. Create `res/values-XX/strings.xml` (where `XX` is the ISO 639-1 code, e.g. `de`, `es`, `ar`)
2. Translate all strings from `res/values/strings.xml`
3. Add the language to `LocaleUtils.getSupportedLanguages()`

## License

MIT License — see [LICENSE](LICENSE)
