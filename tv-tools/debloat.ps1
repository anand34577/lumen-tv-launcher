# Reversible debloat profile, written for a Realtek-based Acer Android TV 14 set.
# Review the list below and adjust it for your TV before running it.
# Uses only "pm disable-user": nothing is uninstalled and no app data is wiped.
# Undo everything with .\restore.ps1, or one app with:  adb shell pm enable <package>
#
# Deliberately kept (commonly used, or needed by the TV): YouTube, Netflix, Prime Video, Zee5, Hotstar,
# voice search (katniss), Chromecast (mediashell), screen mirroring (scast, fastscreen),
# Play Store, OTA updates, all com.apps.* TV firmware (inputs, picture/sound menus, tuner).
param(
    [string]$Device,
    [switch]$Yes          # skip the confirmation prompt
)
$ErrorActionPreference = "Stop"
# TV address: -Device, else $env:TV_DEVICE, else tv-tools\device.txt (git-ignored).
if (-not $Device) { $Device = $env:TV_DEVICE }
if (-not $Device -and (Test-Path "$PSScriptRoot\device.txt")) { $Device = (Get-Content "$PSScriptRoot\device.txt" -Raw).Trim() }
if (-not $Device) { throw "Set the TV address: -Device 192.168.1.50:5555, the TV_DEVICE environment variable, or tv-tools\device.txt" }

$targets = [ordered]@{
    # Stock launcher stack (replaced by Lumen) -- ~400 MB RAM together
    "com.google.android.tvlauncher"                  = "Stock launcher (replaced by Lumen)"
    "com.google.android.tvrecommendations"           = "Home-screen recommendations / sponsored rows"
    "com.realtek.leanbacklauncher.partnercustomizer" = "Vendor customisation for the stock launcher"
    # Ads, promos, retail
    "com.apps.mk.wd"                                 = "S-Channel promo/ad app (ClickAdActivity)"
    "com.apps.esticker"                              = "Retail-store e-sticker overlay"
    "com.mk.tv.meeting.service"                      = "Meeting mode / digital-signage service"
    "com.google.android.backdrop"                    = "Ambient-mode screensaver (fetches photos online)"
    # Telemetry, logging, feedback
    "com.apps.logapp"                                = "Vendor log collector"
    "com.google.android.feedback"                    = "Google feedback reporter"
    "com.android.tv.feedbackconsent"                 = "Feedback consent prompt"
    "com.android.adservices.api"                     = "Privacy-sandbox ad services"
    "com.android.federatedcompute.services"          = "Federated learning / telemetry"
    "com.android.ondevicepersonalization.services"   = "On-device ad personalisation"
    # Not used on this TV
    "com.google.android.youtube.tvmusic"             = "YouTube Music"
    "com.seraphic.hbbtv.app"                         = "HbbTV broadcast overlay (no antenna/cable use)"
    "com.google.android.play.games"                  = "Google Play Games"
    # Unused system extras
    "com.android.printspooler"                       = "Printing"
    "com.android.wallpaper.livepicker"               = "Live-wallpaper picker"
    "com.android.dynsystem"                          = "Dynamic system updates (developer feature)"
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found. Install Android SDK Platform-Tools and add its folder to PATH."
}

adb connect $Device | Out-Null
if ((adb -s $Device get-state 2>$null) -ne "device") { throw "TV $Device is not reachable over ADB." }
# From here on, adb reports problems on stderr; log them and keep going instead of stopping halfway.
$ErrorActionPreference = "Continue"

# Without Lumen installed, disabling the stock launcher would leave the TV with no home screen.
$lumen = adb -s $Device shell pm list packages com.lumen.tvlauncher
if (-not $lumen) { throw "Lumen is not installed. Install it first (see docs/INSTALL.md); nothing was changed." }

if (-not $Yes) {
    Write-Host "`nThese packages will be disabled (nothing is uninstalled):" -ForegroundColor Cyan
    foreach ($p in $targets.Keys) { Write-Host ("  {0,-48} {1}" -f $p, $targets[$p]) }
    if ((Read-Host "`nType YES to continue") -ne "YES") { Write-Host "Cancelled. Nothing was changed."; exit 1 }
}

$backups = Join-Path $PSScriptRoot "..\backups"
if (-not (Test-Path $backups) -or -not (Get-ChildItem $backups -Directory)) {
    Write-Host "No backup found - creating one first." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot "backup.ps1") -Device $Device | Out-Null
}

# Make Lumen the home app before the stock launcher goes away.
adb -s $Device shell cmd role add-role-holder --user 0 android.app.role.HOME com.lumen.tvlauncher 0 | Out-Null

$log = Join-Path $backups ("debloat-log_" + (Get-Date -Format "yyyy-MM-dd_HH-mm-ss") + ".txt")
$installed = adb -s $Device shell pm list packages
foreach ($p in $targets.Keys) {
    if ($installed -notcontains "package:$p") { $r = "not installed - skipped" }
    else { $r = (adb -s $Device shell pm disable-user --user 0 $p 2>&1 | Out-String).Trim() }
    $line = "{0,-48} {1,-50} {2}" -f $p, $targets[$p], $r
    Write-Host $line
    Add-Content -Encoding utf8 $log $line
}

adb -s $Device shell input keyevent KEYCODE_HOME | Out-Null
Write-Host "`nDone. Log: $log" -ForegroundColor Green
Write-Host "Undo all:  .\restore.ps1      Undo one:  adb -s $Device shell pm enable <package>"
