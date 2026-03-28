# Velo Browser — Production-Ready Android Browser App

## 1. Full App Architecture

### Layer Structure

```
┌─────────────────────────────────────────────────────┐
│                     UI Layer                        │
│  Activities, Fragments, ViewModels, Adapters        │
│  Material Design Components, View Binding           │
├─────────────────────────────────────────────────────┤
│                   Domain Layer                      │
│  Use Cases, Repository Interfaces, Business Logic   │
├─────────────────────────────────────────────────────┤
│                    Data Layer                       │
│  Room DB, DataStore, Repositories (impl)            │
│  WebView Engine, Download Manager, Ad Blocker       │
└─────────────────────────────────────────────────────┘
```

### Folder Structure

```
app/src/main/
├── java/com/velobrowser/
│   ├── ui/
│   │   ├── browser/
│   │   │   ├── BrowserActivity.kt         ← Main browser screen
│   │   │   ├── BrowserViewModel.kt        ← Browser state management
│   │   │   ├── WebViewFragment.kt         ← WebView wrapper
│   │   │   └── TabsBottomSheet.kt         ← Tab switcher UI
│   │   ├── tabs/
│   │   │   ├── TabsAdapter.kt             ← RecyclerView adapter for tabs
│   │   │   └── TabItemView.kt             ← Single tab card view
│   │   ├── settings/
│   │   │   ├── SettingsActivity.kt        ← Settings root screen
│   │   │   ├── SettingsFragment.kt        ← Preference fragment
│   │   │   └── LanguageSettingsFragment.kt← Language picker
│   │   ├── profiles/
│   │   │   ├── ProfileManagerActivity.kt  ← Profile list/manage
│   │   │   ├── ProfileManagerViewModel.kt
│   │   │   └── ProfileAdapter.kt
│   │   └── downloads/
│   │       ├── DownloadsActivity.kt       ← Downloads list
│   │       └── DownloadsAdapter.kt
│   ├── core/
│   │   ├── browser/
│   │   │   ├── VeloWebViewClient.kt       ← Custom WebViewClient (ad block, safe browsing)
│   │   │   ├── VeloWebChromeClient.kt     ← Progress, title, favicon updates
│   │   │   └── WebViewFactory.kt          ← Factory for creating configured WebViews
│   │   ├── tabs/
│   │   │   ├── Tab.kt                     ← Tab model
│   │   │   └── TabManager.kt              ← Singleton managing open tabs
│   │   ├── adblocker/
│   │   │   ├── AdBlocker.kt               ← Request interception logic
│   │   │   └── FilterListParser.kt        ← Parses ABP/EasyList rules
│   │   └── download/
│   │       ├── VeloDownloadManager.kt     ← Download queue + progress
│   │       └── DownloadNotificationHelper.kt
│   ├── domain/
│   │   ├── model/
│   │   │   ├── BrowserTab.kt              ← Tab domain model
│   │   │   ├── Profile.kt                 ← Profile domain model
│   │   │   ├── DownloadItem.kt            ← Download domain model
│   │   │   ├── HistoryEntry.kt            ← History domain model
│   │   │   └── BookmarkEntry.kt
│   │   ├── repository/
│   │   │   ├── ProfileRepository.kt       ← Profile repo interface
│   │   │   ├── HistoryRepository.kt
│   │   │   ├── BookmarkRepository.kt
│   │   │   └── DownloadRepository.kt
│   │   └── usecase/
│   │       ├── GetProfilesUseCase.kt
│   │       ├── CreateProfileUseCase.kt
│   │       ├── DeleteProfileUseCase.kt
│   │       ├── AddHistoryUseCase.kt
│   │       └── ClearHistoryUseCase.kt
│   ├── data/
│   │   ├── local/
│   │   │   ├── db/
│   │   │   │   ├── VeloDatabase.kt        ← Room Database
│   │   │   │   ├── dao/
│   │   │   │   │   ├── ProfileDao.kt
│   │   │   │   │   ├── HistoryDao.kt
│   │   │   │   │   ├── BookmarkDao.kt
│   │   │   │   │   └── DownloadDao.kt
│   │   │   │   └── entity/
│   │   │   │       ├── ProfileEntity.kt
│   │   │   │       ├── HistoryEntity.kt
│   │   │   │       ├── BookmarkEntity.kt
│   │   │   │       └── DownloadEntity.kt
│   │   │   └── datastore/
│   │   │       └── SettingsDataStore.kt   ← Preferences via DataStore
│   │   └── repository/
│   │       ├── ProfileRepositoryImpl.kt
│   │       ├── HistoryRepositoryImpl.kt
│   │       ├── BookmarkRepositoryImpl.kt
│   │       └── DownloadRepositoryImpl.kt
│   ├── utils/
│   │   ├── UrlUtils.kt                    ← URL detection, search query building
│   │   ├── LocaleUtils.kt                 ← Language switch helpers
│   │   ├── PermissionUtils.kt             ← Runtime permission helpers
│   │   ├── ClipboardUtils.kt
│   │   └── Extensions.kt                  ← Kotlin extension functions
│   └── di/
│       └── AppModule.kt                   ← Hilt module (DB, DataStore, Repos)
└── res/
    ├── layout/                            ← XML layouts
    ├── values/strings.xml                 ← English strings
    ├── values-fr/strings.xml              ← French strings
    ├── values/colors.xml
    ├── values/themes.xml
    ├── drawable/                          ← Icons and vector assets
    ├── raw/
    │   └── adblock_rules.txt              ← Bundled filter list
    └── xml/
        ├── network_security_config.xml    ← HTTPS enforcement
        └── preferences.xml               ← Settings preference screen
```

---

## 2. Features Breakdown

### Core Browser
- WebView-based rendering with optimized settings
- Smart URL bar: auto-detect URL vs search query
- Navigation: Back, Forward, Refresh, Stop
- Progress bar with animated indicator
- Page title and favicon display
- Full-screen mode for video
- Desktop mode toggle (user-agent switching)
- Safe browsing via Google SafeBrowsing API

### Multi-Profile System
- Each profile gets its own isolated WebView cookie storage
- Profiles stored in Room DB with name, color, and avatar initial
- Profile switching recreates WebView with new CookieManager context
- Profile-specific history tracking
- Profile-specific session cookies via CookieManager.setAcceptCookie()

### Ad Blocker System
- Filter list bundled in `res/raw/adblock_rules.txt` (EasyList format, simplified)
- Parsed at app startup into a HashSet of domain rules + regex patterns
- WebViewClient.shouldInterceptRequest() checks every resource URL
- Returns empty response (WebResourceResponse) for blocked requests
- Toggle via Settings DataStore preference

### Settings System (DataStore)
- JavaScript enabled/disabled
- Images enabled/disabled
- Ad blocker on/off
- Ultra Fast Mode (blocks images + heavy scripts)
- Default search engine (Google, DuckDuckGo, Bing, Brave)
- Homepage URL
- Clear cache / Clear history / Clear cookies
- Language preference
- Dark mode sync with system

### Language System
- `res/values/strings.xml` (English)
- `res/values-fr/strings.xml` (French)
- `LocaleUtils.kt` applies locale override at app startup
- Language picker in Settings that restarts activity

### Download Manager
- Intercepts download via `WebView.setDownloadListener()`
- Custom `VeloDownloadManager` wraps Android's `DownloadManager` system service
- Shows progress notification via `DownloadNotificationHelper`
- Downloads list screen backed by Room DB `DownloadEntity`

### Privacy & Security
- Incognito mode: in-memory cookies, no history written
- HTTPS preference enforced via `network_security_config.xml`
- Safe Browsing enabled (`WebSettings.safeBrowsingEnabled = true`)
- File access restricted (`setAllowFileAccessFromFileURLs(false)`)
- No mixed content allowed in strict mode

---

## 3. Technical Decisions

### Why WebView
- Minimal binary footprint (no Chromium embedding)
- Native Android integration
- Sufficient for all use cases (browsing, downloads, JS execution)
- Mirrors Via Browser's approach

### Cookie / Session Isolation
- Android `CookieManager` is global, so we implement profile isolation by:
  1. Flushing and saving current profile's cookies to Room DB on profile switch
  2. Clearing global CookieManager
  3. Loading the new profile's cookies into CookieManager
  4. Incognito mode never persists — `WebView.clearCache()` + ephemeral cookie store

### Ad Blocking
- EasyList-style rules parsed to: exact domains, wildcard patterns, regex
- Each network request URL checked against these lists in `shouldInterceptRequest`
- Returns null (allow) or empty `WebResourceResponse` (block)
- Performance: HashSet lookup for domain-exact rules (O(1)), regex only as fallback

### Storage
- **Room** for structured data: profiles, history, bookmarks, downloads
- **DataStore (Preferences)** for flat settings key-value pairs
- **SharedPreferences** not used (DataStore is the modern replacement)

---

## 4. UI/UX Structure

### Main Browser Screen (BrowserActivity)
```
┌──────────────────────────────────────┐
│  [←] [→] [🔄]  [URL BAR]  [⋮ Menu] │  ← Top toolbar
├──────────────────────────────────────┤
│  ████████████░░░░░ 65%               │  ← Progress bar
├──────────────────────────────────────┤
│                                      │
│            WebView                   │
│                                      │
└──────────────────────────────────────┘
│  [Tabs N] [Home] [Bookmarks] [Menu]  │  ← Bottom navigation bar
└──────────────────────────────────────┘
```

### Tabs System (Bottom Sheet)
- RecyclerView grid of tab cards with screenshot preview
- Swipe-to-close or X button on each card
- Add new tab button (+ FAB)
- Incognito mode indicator

### Settings Screen
- PreferenceFragmentCompat with sections:
  - Browser (JS, images, ad blocker, fast mode)
  - Privacy (incognito default, safe browsing)
  - Search (engine, homepage)
  - Language
  - Data (clear cache, history, cookies)
  - About

### Profile Manager Screen
- RecyclerView list of profiles
- Colored avatar circle with name initial
- Active profile highlighted
- Create / Rename / Delete via dialog

---

## 5. Execution Roadmap

1. **Project Setup** — Gradle, dependencies, folder structure
2. **Data Layer** — Room entities, DAOs, Database class
3. **DataStore** — SettingsDataStore with all preferences
4. **Domain Layer** — Models, repository interfaces, use cases
5. **Repository Implementations** — Wire DAOs to interfaces
6. **Ad Blocker** — FilterListParser + AdBlocker + filter rules file
7. **WebView Core** — VeloWebViewClient, VeloWebChromeClient, WebViewFactory
8. **Tab Manager** — In-memory tab state management
9. **Browser UI** — BrowserActivity, BrowserViewModel, URL bar, nav controls
10. **Profile Manager UI** — ProfileManagerActivity + ViewModel
11. **Settings UI** — SettingsActivity, SettingsFragment, DataStore bindings
12. **Download Manager** — VeloDownloadManager, notification, DownloadsActivity
13. **Language System** — LocaleUtils, strings.xml (EN + FR)
14. **Security** — network_security_config.xml, permission declarations
15. **GitHub Actions** — CI/CD pipeline for build + APK artifact
