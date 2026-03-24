# USB OTG Disk Manager — Android App (100% Kotlin)

Projet Android natif — aucun Node.js, aucun TypeScript.

## Architecture

```
/ (racine = racine du repo GitHub)
├── app/                         # Module principal (UI, ViewModels, Navigation)
├── core/                        # Modèles, utilitaires, entités
├── usb/                         # Détection USB OTG, permissions, formatage
├── storage/                     # Opérations fichiers (SAF + accès direct)
├── gradle/
│   ├── libs.versions.toml       # Catalogue de dépendances
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── .github/
│   └── workflows/
│       └── build.yml            # CI GitHub Actions → APK signé arm64-v8a
├── gradlew / gradlew.bat
├── build.gradle.kts             # Root build
├── settings.gradle.kts
├── status.py                    # Serveur de statut (Replit uniquement)
└── replit.md                    # Ce fichier
```

## Stack technique

| Composant | Technologie |
|---|---|
| Langage | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt 2.54 |
| Async | Coroutines + StateFlow |
| USB | UsbManager API + libaums |
| Fichiers | SAF (Storage Access Framework) + accès direct |
| Navigation | Navigation Compose |
| CI/CD | GitHub Actions → APK arm64-v8a signé |

## Build local

```bash
./gradlew assembleDebug       # APK debug
./gradlew assembleRelease     # APK release (nécessite keystore)
```

## GitHub Actions

Secrets à configurer dans le repo GitHub (Settings → Secrets → Actions) :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | Keystore encodé en base64 |
| `KEYSTORE_PASSWORD` | `ferelONDONGO1631@` |
| `KEY_ALIAS` | `ferelONDONGO1631@` |
| `KEY_PASSWORD` | `ferelONDONGO1631@` |

## Pousser vers GitHub OTG

Dans le Shell Replit :
```bash
bash push.sh https://github.com/ferelking242/OTG.git
```

## Modules

- **:core** — DiskDevice, FileItem, ClipboardState, Extensions, ShellResult
- **:usb** — UsbDeviceRepository (interface + impl), UsbBenchmarkManager, Hilt DI
- **:storage** — FileRepository (interface + impl), SAF + DocumentFile, Hilt DI
- **:app** — MainActivity, UsbBroadcastReceiver, DiskOperationService, ViewModels, Screens, Theme, Navigation
