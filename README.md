# TachiUp

An Android app for keeping your Tachiyomi/Mihon/Komikku **extensions** up to date from the
**Keiyoushi** store — with optional **silent bulk installs via Shizuku**, one-tap
**auto-trust** setup for Komikku/Mihon, and a community tab linking the store's
**Discord** and **GitHub issues**.

## Features

- **Scan installed extensions** — finds every Tachiyomi/Mihon extension on the device
  (by extension metadata or the `eu.kanade.tachiyomi.extension` package prefix).
- **Update checking** — reads the store's current index (the protobuf `index_v2`
  format that Mihon 0.20.1+/Komikku use) and flags installed extensions whose
  `versionCode` is lower than the store's.
- **One-tap update / Update all** — downloads the matching APK and installs it.
- **Bulk install** — tick any number of extensions in Browse (or "Select … new or
  outdated") and install them in one go. Downloads run 4 at a time and installs are
  queued one by one, with a progress bar; with Shizuku no dialog is shown at all.
- **Uninstall** — every installed extension has an uninstall button (silent via
  Shizuku, or the system dialog otherwise).
- **Browse tab** — searchable catalog of *every* extension across all repos so you
  can install new ones; shows installed/update state, language and an NSFW filter.
- **Signature-mismatch handling** — when an existing extension was signed with a
  different key, the Shizuku path automatically uninstalls and reinstalls so the
  update succeeds.
- **Skip the "Trust" prompt** — Komikku/Mihon automatically trust every extension
  signed with the key of a store added in the app. Settings → *Skip the "Trust"
  prompt* lists the installed readers (Komikku Beta first) and opens their
  "add extension store" dialog with Keiyoushi pre-filled; confirm once and every
  extension TachiUp installs from Keiyoushi loads without a Trust tap, including after
  updates.
- **Foreign-signed extensions** — the Installed tab flags extensions signed with a
  different key than their store (e.g. old Yuzono builds), which the reader can't
  trust through the store, and can swap them all for the store's builds via Shizuku.
- **Shizuku silent installer** — when enabled and Shizuku is running/authorized,
  updates install without confirmation dialogs (APK piped to `pm install`).
  Falls back to the system package installer otherwise.
- **Community tab** — open each repository's Discord and browse its open GitHub
  issues / announcements directly in the app.
- **Diagnostic log** — every scan/download/install step is logged with detailed
  failure reasons (`pm` exit codes + stderr, PackageInstaller status messages).
  Open it from the log icon on the Extensions screen and **copy** or **export/share**
  it as a `.txt`. Signature-mismatch failures are detected and explained; the
  Shizuku path can auto-reinstall in that case.

## Stores targeted

| Store | Registered URL |
|-------|----------------|
| Keiyoushi | `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json` |

Yuzono's repos were deprecated and now point at Keiyoushi, so they were removed.

Keiyoushi's `index.min.json` now only lists "Outdated App" placeholders. Like
Mihon/Komikku, TachiUp follows it to `repo.json`, then to its `index_v2` link — a
gzipped protobuf index with absolute APK URLs (GitHub releases), per-extension content
ratings and the store's signing-key fingerprint. Stores still using the legacy JSON
format keep working. Defined in `app/src/main/java/com/tachiup/data/Repos.kt`; wire
formats in `data/StoreFormat.kt`.

## Project layout

```
app/src/main/java/com/tachiup/
  MainActivity.kt              # Compose entry point, bottom nav, Shizuku wiring
  data/                        # models, repo definitions, network, scanner, settings
  install/                     # Shizuku + PackageInstaller install paths
  ui/                          # AppViewModel + Compose screens + theme
```

## Building

Requires the Android SDK (compileSdk 34, build-tools 34.0.0) and JDK 17+.

```bash
# debug
gradle :app:assembleDebug

# unit tests (store index parsing, update/trust state)
gradle :app:testDebugUnitTest

# release (unsigned), then sign with your own keystore
gradle :app:assembleRelease
zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
apksigner sign --ks <keystore> --ks-key-alias <alias> --out TachiUp.apk aligned.apk
```

Current version: **versionCode 11 / versionName v11**.

## Uploading a build to Google Drive

`scripts/upload_to_drive.py` uploads an artifact to `Drive/TachiUpi/<iteration>/`.
Credentials are read from the environment (never commit them):

```bash
export GDRIVE_CLIENT_ID=... GDRIVE_CLIENT_SECRET=... GDRIVE_REFRESH_TOKEN=...
python3 scripts/upload_to_drive.py TachiUp-v11.apk v11 TachiUpi
```

## Notes

- The app requests `QUERY_ALL_PACKAGES` so it can detect installed extensions, and
  `REQUEST_INSTALL_PACKAGES` for the non-Shizuku install path.
- Discord invite links are best-effort defaults; adjust them in `Repos.kt` if a
  community changes its invite.
- TachiUp can't flip Komikku's trust setting itself: its data is private to the app,
  and Shizuku runs as the shell user, which can't write there. Adding the store in
  Komikku is the supported way, and it covers every current and future Keiyoushi
  extension.
- This is a self-signed test build. Installing it next to a previous TachiUp build
  signed with a different key requires uninstalling the old one first.
