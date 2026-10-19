# Troubleshooting

Find your problem below. Most fixes need `adb` connected to the TV
([Install guide, step 2](INSTALL.md#2-connect-from-your-computer)).

- [Pressing Home opens the old launcher](#pressing-home-opens-the-old-launcher)
- [The TV boots to a plain settings screen](#the-tv-boots-to-a-plain-settings-screen)
- [INSTALL_FAILED_UPDATE_INCOMPATIBLE](#install_failed_update_incompatible)
- [adb: unauthorized, offline or cannot connect](#adb-unauthorized-offline-or-cannot-connect)
- [The notification bell never shows a badge](#the-notification-bell-never-shows-a-badge)
- [An app is missing from the grid](#an-app-is-missing-from-the-grid)
- [The voice search button does nothing](#the-voice-search-button-does-nothing)
- [Inputs says "No external inputs found"](#inputs-says-no-external-inputs-found)
- [A settings entry says "Couldn't open that"](#a-settings-entry-says-couldnt-open-that)
- [The storage alert won't go away](#the-storage-alert-wont-go-away)
- ["No internet access" although the internet works](#no-internet-access-although-the-internet-works)
- [My favourites and settings are gone](#my-favourites-and-settings-are-gone)
- [The TV feels slow right after boot](#the-tv-feels-slow-right-after-boot)

---

### Pressing Home opens the old launcher

Many TVs always send Home to the stock launcher, whatever the default is. Disable it:

```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

On Google TV devices the package is `com.google.android.apps.tv.launcherx`. Undo with
`adb shell pm enable <package>`.

### The TV boots to a plain settings screen

No home app is available. This happens when Lumen is uninstalled while the stock launcher is disabled.
Either bring the stock launcher back:

```bash
adb shell pm enable com.google.android.tvlauncher
```

or install Lumen again. If `adb` isn't enabled on that TV and you can't reach it, a factory reset from
the settings screen also brings the stock launcher back (it erases your accounts and apps).

### INSTALL_FAILED_UPDATE_INCOMPATIBLE

The Lumen already on the TV was signed with a different key (for example, your own build versus an
official release). Android only installs updates signed with the same key. Remove the old one first;
this deletes Lumen's favourites and settings:

```bash
adb shell pm enable com.google.android.tvlauncher     # keep a home screen available
adb uninstall com.lumen.tvlauncher
adb install lumen-v1.2.0.apk
```

Then repeat [step 4 of the install guide](INSTALL.md#4-make-lumen-the-home-screen).

### adb: unauthorized, offline or cannot connect

| Symptom | Fix |
|---|---|
| `unauthorized` | Accept the *Allow USB debugging?* prompt on the TV. If it never appears, turn USB debugging off and on, then run `adb kill-server` and `adb connect` again |
| `offline` | `adb disconnect` then `adb connect <ip>:5555`. Some TVs briefly drop Wi‑Fi when Bluetooth pairing starts |
| `failed to connect` / `connection refused` | Check the TV's IP (it may have changed; reserve it in your router), and that network debugging is still on. Some TVs switch it off after a reboot |
| `more than one device/emulator` | Add `-s <ip>:5555` after `adb` |

### The notification bell never shows a badge

Lumen needs notification access:

```bash
adb shell cmd notification allow_listener com.lumen.tvlauncher/com.lumen.tvlauncher.NotificationService
```

Some low-memory TV builds don't allow notification listeners at all. Everything else in Lumen still
works. Ongoing notifications (music playing, downloads in progress, background services) are left
out on purpose.

### An app is missing from the grid

- It may be hidden: look for the **Hidden · N** chip next to the Apps title.
- It may not have a launcher entry. Background services, plugins and some system components can't be
  opened as apps, so no launcher shows them.
- It may be disabled: `adb shell pm list packages -d` lists disabled packages; `adb shell pm enable
  <package>` brings one back.
- Settings apps are reached through ⚙ rather than listed in the grid.

### The voice search button does nothing

Lumen asks the system for its assistant or search. If the TV's voice app (`com.google.android.katniss`
on most Android TVs) is disabled or missing, there's nothing to open. Re-enable it with
`adb shell pm enable com.google.android.katniss`. The microphone button on the remote is handled by the
TV itself and works independently of Lumen.

### Inputs says "No external inputs found"

The TV reports no HDMI or AV inputs to Android. That's normal on streaming boxes and sticks. On a TV,
check that the vendor's input/tuner apps (often `com.<vendor>.livetv`, `.atv`, `.dtv` or
`.passthrough`) weren't disabled.

### A settings entry says "Couldn't open that"

TV makers rename or remove some settings screens. Lumen tries a fallback first (for example
**Picture** falls back to **Display & sound**); if neither exists you'll see this message. Use
**All settings** instead.

### The storage alert won't go away

The alert shows when less than 1 GB (or 10 %) is free, and turns red under 500 MB (or 5 %). These
limits are deliberately cautious, because app updates fail with less. To free space, open
**⚙ → Apps**, then pick large apps and clear their cache or uninstall the ones you don't use. The alert
updates within a minute.

### "No internet access" although the internet works

Android checks connectivity by contacting Google's servers. If that check is blocked (some DNS filters,
firewalls or networks in restricted regions), Android reports *no internet* even though apps work.
Lumen only repeats what Android reports. Fix the network check, or ignore the alert.

### My favourites and settings are gone

Lumen keeps them on the TV in its own storage. They are lost when Lumen is uninstalled (including when
installing a build signed with a different key), when you clear its data in **App info**, or after a
factory reset. **Reset home screen** also clears them on purpose.

### The TV feels slow right after boot

For the first minute after boot, Android starts every background service at once. Lumen loads apps
in the background and shows them as soon as they're ready. If the TV is still slow after that, check
memory with `adb shell dumpsys meminfo` and see [TV tools](TV-TOOLS.md) for disabling apps you don't
use.

---

Still stuck? [Open an issue](../../../issues) with your TV model, Android version, what you did and
what happened.
