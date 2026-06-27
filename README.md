# TachiUp

An Android app for keeping your Tachiyomi/Mihon **extensions** up to date from the
**Keiyoushi** and **Yuzono** repositories — with optional **silent installs via Shizuku**
and a community tab linking each repo's **Discord** and **GitHub issues**.

## Features

- **Scan installed extensions** — finds every Tachiyomi/Mihon extension on the device
  (by extension metadata or the `eu.kanade.tachiyomi.extension` package prefix).
- **Update checking** — fetches `index.min.json` from both repos and flags which
  installed extensions have a newer version available.
- **One-tap update / Update all** — downloads the matching APK and installs it.
- **Shizuku silent installer** — when enabled and Shizuku is running/authorized,
  updates install without confirmation dialogs (APK piped to `pm install`).
  Falls back to the system package installer otherwise.
- **Community tab** — open each repository's Discord and browse its open GitHub
  issues / announcements directly in the app.

## Repositories targeted

| Repo | Index |
|------|-------|
| Keiyoushi | `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json` |
| Yuzono | `https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json` |

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

Current version: **versionCode 8 / versionName v8**.

## Uploading a build to Google Drive

`scripts/upload_to_drive.py` uploads an artifact to `Drive/TachiUpi/<iteration>/`.
Credentials are read from the environment (never commit them):

```bash
export GDRIVE_CLIENT_ID=... GDRIVE_CLIENT_SECRET=... GDRIVE_REFRESH_TOKEN=...
python3 scripts/upload_to_drive.py TachiUp-v8.apk v8 TachiUpi
```

## Notes

- The app requests `QUERY_ALL_PACKAGES` so it can detect installed extensions, and
  `REQUEST_INSTALL_PACKAGES` for the non-Shizuku install path.
- Discord invite links are best-effort defaults; adjust them in `Repos.kt` if a
  community changes its invite.
- This is a self-signed test build. Installing it next to a previous TachiUp build
  signed with a different key requires uninstalling the old one first.
