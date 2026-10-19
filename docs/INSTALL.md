# Installing Lumen

This guide takes you from a stock Android TV to Lumen as the home screen, and back again if you change
your mind. Nothing here roots the TV or voids anything; every step can be undone.

- [Before you start](#before-you-start)
- [1. Turn on developer options and debugging](#1-turn-on-developer-options-and-debugging)
- [2. Connect from your computer](#2-connect-from-your-computer)
- [3. Install the APK](#3-install-the-apk)
- [4. Make Lumen the home screen](#4-make-lumen-the-home-screen)
- [5. Allow notification access (optional)](#5-allow-notification-access-optional)
- [Updating](#updating)
- [Going back to the stock launcher](#going-back-to-the-stock-launcher)
- [Uninstalling](#uninstalling)

## Before you start

**Supported**
- Android TV 8.0 (API 26) or newer, including Android TV 14.
- Tested on an Acer (Realtek) Android TV 14 set with 1.5 GB of RAM.

**Probably fine, not tested**
- Google TV devices. The stock launcher there is `com.google.android.apps.tv.launcherx` instead of
  `com.google.android.tvlauncher`; use that name in the commands below.

**Not supported**
- Amazon Fire TV. Fire OS always sends the Home button to Amazon's own launcher and doesn't let you
  replace it.

**You need**
- A computer (Windows, macOS or Linux) on the same network as the TV.
- [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools), which
  provides `adb`. Unzip it and either add the folder to your `PATH` or run `adb` from inside it.
- The latest `lumen-vX.Y.Z.apk` from the [releases page](../../../releases/latest). The `.sha256` file
  next to it lets you check the download (see [Verifying the download](#verifying-the-download)).

## 1. Turn on developer options and debugging

Menu names vary a little between brands; these are the usual ones.

1. Open **Settings → Device Preferences → About** (on some TVs: **Settings → System → About**).
2. Select **Build** (sometimes *Android TV OS build*) seven times. You'll see *"You are now a developer"*.
3. Go back to **Device Preferences → Developer options** and turn on **USB debugging**.
   If there is a separate **Network debugging** or **ADB over network** switch, turn that on too.
4. Find the TV's IP address under **Settings → Network & Internet → (your network)**.

> Some TVs turn network debugging off after a reboot. If `adb connect` stops working later, check this
> switch first.

## 2. Connect from your computer

```bash
adb connect 192.168.1.50:5555        # replace with your TV's IP; 5555 is the usual port
adb devices
```

The first time, the TV asks **"Allow USB debugging?"**. Tick **Always allow from this computer** and
choose **OK**. `adb devices` should now list the TV as `device`.

| `adb devices` shows | Meaning | Fix |
|---|---|---|
| `unauthorized` | The prompt on the TV wasn't accepted | Look at the TV, accept the prompt, run `adb devices` again |
| `offline` | Connection dropped | `adb disconnect`, then `adb connect <ip>:5555` again |
| nothing / `failed to connect` | Wrong IP, debugging off, or different network | Re-check the IP and the debugging switch; make sure both devices are on the same network |

If more than one device is connected, add `-s <ip>:5555` after `adb` in every command below.

## 3. Install the APK

```bash
adb install -r lumen-v1.2.0.apk
```

`Success` means it's installed. Nothing changes on screen yet.

If you get `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a copy of Lumen signed with a different key is already
on the TV. See [Troubleshooting](TROUBLESHOOTING.md#install_failed_update_incompatible).

### Verifying the download

```bash
# macOS / Linux
shasum -a 256 lumen-v1.2.0.apk
# Windows (PowerShell)
Get-FileHash lumen-v1.2.0.apk -Algorithm SHA256
```

The hash must match the one in `lumen-v1.2.0.apk.sha256`.

## 4. Make Lumen the home screen

First, set Lumen as the default home app:

```bash
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.lumen.tvlauncher 0
adb shell input keyevent KEYCODE_HOME
```

If Lumen appears, you're done.

Many Android TV builds ignore the default and always send the Home button to the stock launcher. If
pressing Home still shows the old launcher, disable it:

```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell input keyevent KEYCODE_HOME
```

This only hides the stock launcher for your user; it stays installed and keeps its data. Bring it back
any time with `adb shell pm enable com.google.android.tvlauncher`.

> **Important:** while the stock launcher is disabled, don't uninstall Lumen without re-enabling the
> stock launcher first. Otherwise the TV has no home screen and falls back to a bare settings screen.
> [Uninstalling](#uninstalling) shows the safe order.

## 5. Allow notification access (optional)

The bell in the top bar shows a badge and a list of notifications. Android needs your permission for
that:

```bash
adb shell cmd notification allow_listener com.lumen.tvlauncher/com.lumen.tvlauncher.NotificationService
```

On TVs that have the screen for it, you can also select the bell in Lumen; without permission it opens
**Notification access** so you can switch it on there. Everything else works without this permission.

## Updating

Install the new APK over the old one. Your favourites, names and settings are kept:

```bash
adb install -r lumen-v1.3.0.apk
```

Updates only install over a build signed with the same key. Official releases are always signed with
the same key.

## Going back to the stock launcher

```bash
adb shell pm enable com.google.android.tvlauncher
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.tvlauncher 0
adb shell input keyevent KEYCODE_HOME
```

Lumen stays installed and can be made the home app again later.

## Uninstalling

Always restore the stock launcher **before** removing Lumen:

```bash
adb shell pm enable com.google.android.tvlauncher
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.tvlauncher 0
adb uninstall com.lumen.tvlauncher
```

If you used the [TV tools](TV-TOOLS.md), `restore.ps1 -RemoveLumen` does all of this, and also
re-enables everything the debloat script turned off.

**Last resort:** a factory reset (Settings → Device Preferences → Reset) removes Lumen and re-enables
every app. It also erases your accounts and apps, so try the steps above first.
