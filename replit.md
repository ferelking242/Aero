# USB OTG Disk Manager — Android App (100% Kotlin)

## Dernières modifications (2026-04-03)
- **GameCard** : boutons Convertir / Sélectionner / Supprimer agrandis et déplacés en bas de la carte (texte + icône visible)
- **PS2 Studio toolbar** : 7 boutons regroupés — 3 visibles (téléchargements, disposition, multi-sélection) + menu ⋮ overflow (tri, récupérer jaquettes, ajouter dossier, scanner)
- **Gestionnaire téléchargements** : s'ouvre directement en plein écran (plus de drag bloqué) ; progression, vitesse et ETA améliorés
- **Jaquettes PS2** : intégration du repo GitHub `xlenore/ps2-covers` comme source primaire ; sélection du type de cover (Standard/2D/3D) au clic sur la vignette
- **FloatingNavDock** : converti en `NavigationBar` propre dans le Scaffold — ne bloque plus aucun bouton (CFG tab "Ajouter", etc.) ; animations améliorées (scale + spring)
- **Shizuku** : bouton "Lancer Shizuku" dans le dialogue quand l'état est NotRunning — exécute le script de démarrage `start.sh` directement depuis l'app


Projet Android natif — aucun Node.js, aucun TypeScript.

## Architecture

```
/ (racine = racine du repo GitHub)
├── app/                         # Module principal (UI, ViewModels, Navigation)
├── core/                        # Modèles, utilitaires, entités
├── usb/                         # Détection USB OTG, permissions, formatage
├── storage/                     # Opérations fichiers (SAF + accès direct)
├── ps2/                         # Module PS2 Studio (conversion, téléchargement, fusion)
├── gradle/
│   ├── libs.versions.toml       # Catalogue de dépendances
│   └── wrapper/
├── .github/
│   └── workflows/
│       └── build.yml            # CI GitHub Actions → APK signé arm64-v8a
├── gradlew / gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
└── replit.md
```

## Stack technique

| Composant | Technologie |
|---|---|
| Langage | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt 2.54 |
| Async | Coroutines + StateFlow |
| USB | UsbManager API |
| Fichiers | SAF (Storage Access Framework) + accès direct |
| Navigation | Navigation Compose |
| CI/CD | GitHub Actions → APK arm64-v8a signé |

## Structure dossiers (sur Android)

```
sdcard/usbdiskmanager/PS2Manager/
  ├── ISO/        ← fichiers ISO scannés
  ├── UL/         ← fichiers UL convertis (défaut)
  ├── ART/        ← pochettes de jeux
  └── Downloads/  ← ISO téléchargés
```
Sur USB : fichiers UL toujours à la racine (imposé par OPL).

## applicationId

`com.diskforge.usbmanager` (namespace code : `com.usbdiskmanager`)

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease     # nécessite keystore
```

## Fonctionnalités PS2 Studio

- **Dock flottant** : toujours visible, pill animée, pas d'auto-masquage, mode immersif (sans barre Android)
- **Tab bar** style Telegram : Jeux / Fusionner CFG / UL Manager / Télécharger / Transfert
- **Multi-sélection** : appui long pour activer, "Tout sélectionner", conversion groupée
- **Destination de conversion** : Défaut (interne) / USB (racine) / Personnalisé
- **Vérification FAT32** avant écriture USB — avertissement si non-FAT32
- **Fusion ul.cfg** : merge illimité de fichiers (2, 100+), édition des noms avant fusion, fusion directe sans édition
  - `writeEntriesPublic()` exposé dans UlCfgManager pour permettre l'écriture depuis l'UI
- **Download manager** : HTTP avec reprise (Range), pause, resume, retry
- **Téléchargement ISO** : recherche via Internet Archive (archive.org) API publique — résolution automatique du lien téléchargeable
- **Pochettes** : GameTDB (cover/M/HQ, multi-région) + PSDB.net fallback ; bouton "Fetch All"
- **Scan téléphone** : `IsoScanner.scanPhoneStorage()` — scan automatique des dossiers communs (Download, Documents, ISO, PS2, ROMs, etc.) en filtrant par ID PS2 valide ; lancé au démarrage en arrière-plan
- **Accès stockage** : `MANAGE_EXTERNAL_STORAGE` demandé automatiquement au démarrage ; `hasStorageAccess` mis à jour au retour des paramètres
- **Transfert USB ↔ Interne** : liste les jeux UL d'une USB, copie vers interne, stockage interne par défaut, ou dossier personnalisé (via folder picker) ; si 2e USB branchée : transfert USB→USB direct
  - Tab auto-rafraîchi à chaque sélection de l'onglet Transfert
  - 1 USB : destination = interne (défaut) OU dossier personnalisé
  - 2+ USB : destination = interne OU dossier personnalisé OU 2e USB directement
  - `UsbGameTransferManager` : lit ul.cfg, copie les parties UL, met à jour ul.cfg destination

## Auto-mount USB

- `UsbMonitorService` : auto-monte chaque USB à la connexion (délai 2,5s) + au démarrage du service (délai 3s pour les devices déjà connectés)
- `FilesystemChecker.listExternalMounts()` : déduplication améliorée par nom de volume ET par device block pour éviter la détection dupliquée de la même USB

## GitHub Actions — Secrets

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | Keystore encodé en base64 |
| `KEYSTORE_PASSWORD` | `ferelONDONGO1631@` |
| `KEY_ALIAS` | `ferelONDONGO1631@` |
| `KEY_PASSWORD` | `ferelONDONGO1631@` |

## Modules

- **:core** — DiskDevice, FileItem, Extensions, ShellResult
- **:usb** — UsbDeviceRepository (interface + impl), UsbBenchmarkManager, Hilt DI
- **:storage** — FileRepository (interface + impl), SAF + DocumentFile, Hilt DI
- **:ps2** — IsoScanner, IsoEngine, UlCfgManager, DownloadEngine, FilesystemChecker, Ps2ViewModel, Ps2StudioScreen, UlCfgMergerScreen, Ps2DownloadScreen
- **:app** — MainActivity (mode immersif), AppNavHost (dock fixe), Screens, Theme, Navigation
