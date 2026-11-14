# MindYourApps

A tiny, private Android app that asks **"Are you sure?"** before you open the apps you choose.

When you open a watched app, Mind Your Apps instantly covers it with a friendly check-in that shows how often you've opened it today and how long you've spent in it. Every choice is one tap:

| Button | What happens |
|---|---|
| **∞** | Opens the app and doesn't ask again until you leave it |
| **5 / 10 / 15 min** | Opens the app and checks in again after that long |
| **Custom time…** | Pick any number of minutes |
| *Back / home gesture* | Leaves the app and goes home |

- **Four looks:** classic blue, pastel, dark minimal, and a live blur of the app behind (Android 12+; falls back to a frosted tint).
- **Friendly lines:** 20 built-in check-ins, picked at random. Add your own, or set a message per app.
- **Private:** no internet permission, no account, no analytics. Everything stays on your phone.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download the `.apk`.
2. Open it and allow installs from your browser or file manager if Android asks.
3. Open **Mind Your Apps** and tap **Open accessibility settings**, then switch on *Mind Your Apps*.
4. Tap **+** and choose your apps.

**Android 13+:** if the accessibility switch is greyed out, open the app's *App info* page, tap the three dots at the top right and choose **Allow restricted settings**. The app has a button that takes you there.

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
  Lines.kt          friendly sentences
  Store.kt          settings, watched apps, daily opens and time
  MainActivity.kt   home, app picker, per-app stats, settings
```

## Privacy

Mind Your Apps never connects to the internet and collects nothing. Open counts and time spent are stored in a small database on your device and the last 60 days are kept.

## License

MIT, see [LICENSE](LICENSE).
