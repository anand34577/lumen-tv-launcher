# TV tools: backup, debloat, restore

`tv-tools/` holds three PowerShell scripts for cleaning up an Android TV without risk:

| Script | Double-click shortcut | Purpose |
|---|---|---|
| `backup.ps1` | `BACKUP-now.cmd` | Take a snapshot of the TV's current state |
| `debloat.ps1` | — (run it from a terminal so you can read the list first) | Disable unwanted preinstalled apps |
| `restore.ps1` | `RESTORE-everything.cmd`, `RESTORE-and-remove-Lumen.cmd` | Put the TV back the way the first backup found it |

They run on Windows (PowerShell 5.1 or newer). On macOS or Linux, see
[Doing it by hand](#doing-it-by-hand).

## Ground rules

- **Nothing is uninstalled.** The debloat script only uses `pm disable-user`. Disabled apps stay on the
  TV with their data and come back with one command.
- **A backup always exists first.** The debloat script creates one if there isn't one yet.
- **The stock launcher is never disabled without Lumen installed**, so the TV can't end up without a
  home screen.
- **A factory reset undoes everything** (and also wipes your accounts), so the worst case is always
  recoverable.

## Setup

1. Install [Platform-Tools](https://developer.android.com/tools/releases/platform-tools) and make sure
   `adb` works in a terminal.
2. Enable debugging on the TV and connect once by hand so you can accept the prompt on the TV
   ([Install guide, steps 1–2](INSTALL.md#1-turn-on-developer-options-and-debugging)).
3. Tell the scripts where the TV is, in any one of these ways:
   - pass `-Device 192.168.1.50:5555` each time,
   - set an environment variable: `setx TV_DEVICE 192.168.1.50:5555` (open a new terminal afterwards),
   - or create `tv-tools/device.txt` containing just `192.168.1.50:5555`. This file is git-ignored.

If Windows refuses to run the scripts ("running scripts is disabled on this system"), use the `.cmd`
shortcuts, or start them with `powershell -ExecutionPolicy Bypass -File .\debloat.ps1`.

## backup.ps1

```powershell
.\backup.ps1
```

Creates `backups/<date_time>/` next to the `tv-tools` folder with:

| File | Contents |
|---|---|
| `packages-all.txt`, `-enabled`, `-disabled`, `-thirdparty`, `-paths` | Every package and its state |
| `home-role.txt`, `home-activity.txt` | Which app is the home screen |
| `settings-global.txt`, `-secure`, `-system` | Android settings tables, for reference |
| `display.txt`, `getprop.txt`, `meminfo.txt` | Display size, build properties, memory use |
| `manifest.json` | When and from which device the backup was taken |

Backups contain device identifiers, so `backups/` is git-ignored. Don't share them publicly.

Run a backup before making manual changes too; it costs nothing.

## debloat.ps1

```powershell
.\debloat.ps1          # shows the list and asks you to type YES
.\debloat.ps1 -Yes     # no prompt
```

What it does, in order:

1. Checks that `adb` exists and the TV is reachable.
2. Refuses to continue if Lumen isn't installed.
3. Shows the packages it will disable and waits for **YES**. Anything else cancels without changes.
4. Takes a backup if none exists.
5. Makes Lumen the home app.
6. Disables each listed package that is installed. Missing ones are reported as skipped, and a
   package Android refuses to disable is logged and skipped rather than stopping the run.
7. Writes a log to `backups/debloat-log_<date_time>.txt` and returns to the home screen.

Running it twice is harmless; already-disabled packages stay disabled.

### The included profile

The list in `debloat.ps1` was written for a Realtek-based Acer Android TV 14 set. Each entry has a
comment saying what it is. It covers:

- the stock launcher, its recommendations service and the vendor's launcher customisation,
- vendor promo, ad and retail-demo apps,
- log collection, feedback and telemetry services, and Android's ad-services modules,
- a few apps that weren't used on that TV (YouTube Music, HbbTV, Play Games, print and live-wallpaper
  services).

It deliberately keeps streaming apps, voice search, Chromecast, screen mirroring, the Play Store, system
updates and all of the TV's own firmware apps (inputs, tuner, picture and sound menus).

### Adapting it to your TV

Package names differ between brands, so **read the list and edit it before running it on another TV.**

To see what's on your TV and what's actually using memory:

```bash
adb shell pm list packages -s                 # system apps
adb shell pm list packages -3                 # apps you installed
adb shell dumpsys meminfo | more              # look for "Total RSS by process"
adb shell dumpsys package <name> | more       # what a package contains
```

Good candidates: ad and promo apps, demo/retail mode, log collectors, feedback services, apps you never
open. **Leave alone** anything you don't recognise that belongs to the TV vendor (often `com.<vendor>.*`
or `com.apps.*`), along with Settings, the input/tuner apps, Bluetooth, the keyboard, Play services,
Play Store, setup wizards and system updates. If in doubt, leave it in, or disable it on its own and
use the TV for a day.

Add or remove lines in the `$targets` table:

```powershell
"com.example.bloat" = "What it is",
```

## restore.ps1

```powershell
.\restore.ps1 -DryRun          # show what would change, change nothing
.\restore.ps1                  # restore
.\restore.ps1 -RemoveLumen     # restore and uninstall Lumen
.\restore.ps1 -Backup ..\backups\2026-10-19_17-52-40   # restore to a specific backup
```

Without `-Backup`, the **oldest** backup is used, since that's normally the original state of the TV.

The restore:

1. re-enables every package that is disabled now but was enabled in that backup,
2. re-installs (for your user) any system package present in the backup but missing now,
3. makes the backup's home app the default again,
4. with `-RemoveLumen`, removes Lumen as home and uninstalls it, only after steps 1–3, so the stock
   launcher is already back,
5. presses Home.

Things to know:

- Apps **you** disabled by hand after that backup are re-enabled as well. Use `-DryRun` first if you
  want to see the list.
- Apps installed after the backup are left alone.
- Lumen's own settings are not part of the backup. They live inside Lumen and are kept unless you
  uninstall it.
- A reboot afterwards is recommended so every re-enabled service starts cleanly.

## Doing it by hand

On macOS, Linux, or anywhere without PowerShell, the same steps are plain `adb` commands:

```bash
# backup (the useful part)
adb shell pm list packages -d > disabled-before.txt
adb shell cmd role get-role-holders --user 0 android.app.role.HOME > home-before.txt

# disable / re-enable one app
adb shell pm disable-user --user 0 com.example.bloat
adb shell pm enable com.example.bloat

# list everything currently disabled
adb shell pm list packages -d
```

To undo by hand, enable every package that appears in `pm list packages -d` now but not in
`disabled-before.txt`.

## If something goes wrong

| Problem | Fix |
|---|---|
| A feature you use stopped working after debloat | Find its package in the debloat log and run `adb shell pm enable <package>`, or run the full restore |
| The TV shows a bare settings-style screen instead of a home screen | No home app is enabled. Run `adb shell pm enable com.google.android.tvlauncher`, or reinstall Lumen |
| `adb` loses the TV in the middle of a script | Reconnect (`adb connect <ip>:5555`) and run the script again. Both scripts are safe to repeat |
| You lost the `backups` folder | Run `restore.ps1` with a backup from another TV of the same model, or enable packages by hand: `adb shell pm list packages -d` lists everything that is disabled |

More fixes are in [Troubleshooting](TROUBLESHOOTING.md).
