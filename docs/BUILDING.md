# Building from source

## Requirements

- JDK 17 (the version CI builds with)
- Android SDK with **Platform 34** and Build-Tools 34 or newer
  (Android Studio installs these, or use the command-line `sdkmanager`)
- No other dependencies; the Gradle wrapper downloads Gradle itself

Tell Gradle where the SDK is, either with the `ANDROID_HOME` environment variable or a
`LumenLauncher/local.properties` file (git-ignored):

```properties
sdk.dir=/path/to/Android/Sdk
```

On Windows, escape the path: `sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`.

## Build

```bash
cd LumenLauncher
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

The APK is `LumenLauncher/app/build/outputs/apk/release/app-release.apk`. R8 shrinking is on, which is
why it's so small.

Without signing details the release APK is **unsigned** and Android won't install it. Either sign it
(next section), or build a debug APK for testing, which is signed with your machine's debug key:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Signing locally

Create `LumenLauncher/keystore.properties` (git-ignored, never commit it):

```properties
storeFile=keystore/release.jks
storePassword=your-store-password
keyAlias=your-alias
keyPassword=your-key-password
```

`storeFile` is relative to the `LumenLauncher` folder. Keep the keystore outside git too; `*.jks` is
git-ignored. To create a key, see [Releasing](RELEASING.md#1-create-a-signing-key).

The build reads the same values from environment variables when the file is missing:
`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
CI uses those.

> An APK signed with a different key can't be installed over an existing Lumen. See
> [Troubleshooting](TROUBLESHOOTING.md#install_failed_update_incompatible).

## Project layout

```
LumenLauncher/
  app/build.gradle                     version, SDK levels, signing
  app/src/main/AndroidManifest.xml     HOME activity, notification listener, package queries
  app/src/main/java/com/lumen/tvlauncher/
    MainActivity.java                  home screen: rows, menus, alerts, settings, inputs
    AppLoader.java                     finds launchable apps, banners and icon tints
    TileView.java                      focusable app card and its focus animation
    TvMenu.java                        compact pop-up menu anchored to the view that opened it
    NotificationService.java           notification listener behind the bell
    Prefs.java                         favourites, hidden apps, names, appearance, launch history
    Themes.java                        background gradients
    AppEntry.java, Ui.java             small helpers
  app/src/main/res/                    layout, vector icons, colours, strings
tv-tools/                              backup, debloat and restore scripts (PowerShell)
docs/                                  guides and README images (also published to the wiki)
.github/workflows/build.yml            CI and releases
.github/workflows/wiki.yml             republishes the wiki when docs/ or README.md change
.github/scripts/build_wiki.py          converts docs/ into wiki pages
```

## Design notes

- **No libraries.** Only the Android framework, so there's nothing to update and nothing that phones
  home. Please keep it that way in pull requests.
- **Off the main thread.** App discovery and icon/banner loading run on a single background thread;
  the UI is rebuilt only when the app list or a setting changes.
- **Package visibility.** The manifest queries only `LEANBACK_LAUNCHER` and `LAUNCHER` activities,
  not every package (no `QUERY_ALL_PACKAGES`).
- **Focus first.** Every interactive element must be reachable and visibly focused with a D-pad. Test
  changes with a remote, not just a mouse in the emulator.
- **Firmware differences.** Settings shortcuts try several intents and fall back gracefully, because TV
  makers rename or remove screens.

## Testing on a TV or emulator

- Android Studio's **Television (1080p)** emulator image works for most of the UI. It has no HDMI inputs.
- On a real TV: `adb install -r` the build, then press Home. `adb logcat | grep -i lumen` shows errors.
- Screenshots: `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png`.
