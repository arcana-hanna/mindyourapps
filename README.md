# MindYourApps

A tiny, private Android app that asks **"Are you sure?"** before you open the apps you choose.

When you open a watched app, Mind Your Apps instantly covers it with a calm check-in that shows **your numbers**: the app's icon on the left, how many times you've opened it today on the right, and the total time you've spent in it today in the middle. No lectures, no text to read.

| Control | What happens |
|---|---|
| **Curved arrow** (big, under the logo) | Leaves the app. Nothing is counted, so you can play the game of keeping your opens low |
| **10 min** (bottom right) | Opens the app and checks in again after that long. The time is your choice in *Settings → Reminder time* |
| **No reminder** (bottom left) | Opens the app with no timer. The check-in comes back after you've left the app |

An open only counts once you continue into the app.

The home screen shows a stacked 7-day chart of the time you spent in your minded apps (one color per app), with a toggle to see opens instead.

- **Four looks:** classic blue and pastel follow your system light/dark setting; dark minimal and a live blur of the app behind (Android 12+, falls back to a frosted tint) are always dark.
- **Private:** no internet permission, no account, no analytics. Everything stays on your phone.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download the `.apk`.
2. Open it and allow installs from your browser or file manager if Android asks.
3. Open **Mind Your Apps**. It guides you in two steps: **pick your apps** (popular ones are suggested first), then **turn it on** with one switch in Android's accessibility settings. The app notices when you've done it.

**Android 13+:** if the accessibility switch is greyed out, the app shows how to allow restricted settings (App info, the three dots, *Allow restricted settings*) when you come back with it still off.

To get updates automatically, add this repository to [Obtainium](https://github.com/ImranR98/Obtainium).

## What the accessibility permission is used for

Only to learn **which app just came to the front**. The service listens to window-change events and never reads text, passwords or anything on your screen (`canRetrieveWindowContent` is off).

## Building and releasing (no Android Studio needed)

Everything runs on GitHub Actions.

**Every push** is compiled by `Build`, so mistakes show up early. Its debug APK is attached to the run.

**To publish a release**, either:

- push a tag: `git tag v1.0.0 && git push origin v1.0.0`, or
- go to **Actions → Release → Run workflow** and enter a version such as `1.0.0`.

A few minutes later, **Releases** has a page with `MindYourApps-v1.0.0.apk` (plus a checksum) ready to download.

### Permanent signing key (do this before sharing widely)

Android only installs an update over an existing app if both are signed with the **same key**. Until you add a key, releases are signed with a temporary debug key, which works fine for a first install but can't be updated in place later.

1. In GitHub, open the repository and click **Code → Codespaces → Create codespace** (a free browser terminal, nothing to install).
2. Run `bash scripts/make-keystore.sh`.
3. Add the four printed values under **Settings → Secrets and variables → Actions → New repository secret**: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
4. Save `mindyourapps-release.jks` in a password manager, then delete the codespace. Never commit the file.

Future releases are then signed with your key automatically.

## Project layout

```
app/src/main/java/app/mindyourapps/
  MindService.kt    accessibility service: detects launches, timers, come-back window
  PauseOverlay.kt   the "Are you sure?" screen (plain views, so it appears instantly)
  PauseTheme.kt     the four looks
  Store.kt          settings, watched apps, daily opens and time
  MainActivity.kt   home, app picker, per-app stats, settings
```

## Privacy

Mind Your Apps never connects to the internet and collects nothing. Open counts and time spent are stored in a small database on your device and the last 60 days are kept.

## License

MIT, see [LICENSE](LICENSE).
