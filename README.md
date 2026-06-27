# TachiUp

An Android app for keeping your Tachiyomi/Mihon **extensions** up to date from the
**Keiyoushi** and **Yuzono** repositories — with optional **silent installs via Shizuku**
and a community tab linking each repo's **Discord** and **GitHub issues**.

## Features

- **Scan installed extensions** — finds every Tachiyomi/Mihon extension on the device
  (by extension metadata or the `eu.kanade.tachiyomi.extension` package prefix).
- **Update checking** — fetches `index.min.json` from all repos and flags which
  installed extensions have a newer version available.
- **One-tap update / Update all** — downloads the matching APK and installs it.
- **Uninstall** — every installed extension has an uninstall button (silent via
  Shizuku, or the system dialog otherwise).
- **Browse tab** — searchable catalog of *every* extension across all repos so you
  can install new ones; shows installed/update state, language and an NSFW filter.
- **Signature-mismatch handling** — when an existing extension was signed with a
  different key, the Shizuku path automatically uninstalls and reinstalls so the
  update succeeds.
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

## Repositories targeted

| Repo | Index |
|------|-------|
| Keiyoushi | `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json` |
| Yuzono | `https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json` |
| Yuzono Cursed | `https://raw.githubusercontent.com/yuzono/cursed-manga-repo/repo/index.min.json` |

Defined in `app/src/main/java/com/tachiup/data/Repos.kt`.

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

# release (unsigned), then sign with your own keystore
gradle :app:assembleRelease
zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
apksigner sign --ks <keystore> --ks-key-alias <alias> --out TachiUp.apk aligned.apk
```

Current version: **versionCode 10 / versionName v10**.

## Uploading a build to Google Drive

`scripts/upload_to_drive.py` uploads an artifact to `Drive/TachiUpi/<iteration>/`.
Credentials are read from the environment (never commit them):

```bash
export GDRIVE_CLIENT_ID=... GDRIVE_CLIENT_SECRET=... GDRIVE_REFRESH_TOKEN=...
python3 scripts/upload_to_drive.py TachiUp-v10.apk v10 TachiUpi
```

## Notes

- The app requests `QUERY_ALL_PACKAGES` so it can detect installed extensions, and
  `REQUEST_INSTALL_PACKAGES` for the non-Shizuku install path.
- Discord invite links are best-effort defaults; adjust them in `Repos.kt` if a
  community changes its invite.
- This is a self-signed test build. Installing it next to a previous TachiUp build
  signed with a different key requires uninstalling the old one first.
