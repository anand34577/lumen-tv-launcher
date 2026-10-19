# Return the TV to the state recorded by backup.ps1.
#  - Re-enables every package that is disabled now but was enabled in the backup.
#  - Restores the original home launcher (HOME role).
#  - Optionally removes Lumen with -RemoveLumen.
# Usage: .\restore.ps1 [-Backup <folder>] [-RemoveLumen] [-Device <ip:port>]
# With no -Backup, the OLDEST backup (the original factory-like state) is used.
param(
    [string]$Backup,
    [switch]$RemoveLumen,
    [switch]$DryRun,      # show what would change without touching the TV
    [string]$Device
)
$ErrorActionPreference = "Stop"
# TV address: -Device, else $env:TV_DEVICE, else tv-tools\device.txt (git-ignored).
if (-not $Device) { $Device = $env:TV_DEVICE }
if (-not $Device -and (Test-Path "$PSScriptRoot\device.txt")) { $Device = (Get-Content "$PSScriptRoot\device.txt" -Raw).Trim() }
if (-not $Device) { throw "Set the TV address: -Device 192.168.1.50:5555, the TV_DEVICE environment variable, or tv-tools\device.txt" }

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found. Install Android SDK Platform-Tools and add its folder to PATH."
}

adb connect $Device | Out-Null
if ((adb -s $Device get-state 2>$null) -ne "device") { throw "TV $Device is not reachable over ADB." }
# From here on, adb reports problems on stderr; log them and keep going instead of stopping halfway.
$ErrorActionPreference = "Continue"

if (-not $Backup) {
    $root = Join-Path $PSScriptRoot "..\backups"
    $first = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -First 1
    if (-not $first) { throw "No backups found in $root. Pass one with -Backup <folder>." }
    $Backup = $first.FullName
}
if (-not (Test-Path (Join-Path $Backup "packages-disabled.txt"))) { throw "Not a backup folder: $Backup" }
Write-Host "Restoring from $Backup" -ForegroundColor Cyan

function Run([string]$what, [scriptblock]$cmd) { if ($DryRun) { "would $what" } else { & $cmd } }
function PkgSet($lines) { @($lines | ForEach-Object { "$_".Trim() -replace '^package:', '' } | Where-Object { $_ }) }

$wasDisabled = PkgSet (Get-Content (Join-Path $Backup "packages-disabled.txt"))
$wasAll      = PkgSet (Get-Content (Join-Path $Backup "packages-all.txt"))
$nowDisabled = PkgSet (adb -s $Device shell pm list packages -d)
$nowInstalled = PkgSet (adb -s $Device shell pm list packages)

# 1. Re-enable packages we disabled.
foreach ($p in $nowDisabled) {
    if ($wasDisabled -notcontains $p) {
        $r = Run "enable" { adb -s $Device shell pm enable --user 0 $p 2>&1 }
        Write-Host "  enable  $p  -> $r"
    }
}

# 2. Reinstall (for user 0) any system package that was present but is now missing.
foreach ($p in $wasAll) {
    if ($nowInstalled -notcontains $p) {
        $r = Run "install-existing" { adb -s $Device shell cmd package install-existing --user 0 $p 2>&1 }
        Write-Host "  restore $p  -> $r"
    }
}

# 3. Restore the original launcher.
$origHome = (Get-Content (Join-Path $Backup "home-role.txt") -Raw).Trim()
if (-not $origHome) { $origHome = "com.google.android.tvlauncher" }
Run "set home" { adb -s $Device shell cmd role add-role-holder --user 0 android.app.role.HOME $origHome 0 } | Out-Null
Write-Host "  home role -> $origHome"

if ($RemoveLumen -and -not $DryRun) {
    adb -s $Device shell cmd role remove-role-holder --user 0 android.app.role.HOME com.lumen.tvlauncher 0 2>$null | Out-Null
    $r = adb -s $Device uninstall com.lumen.tvlauncher 2>&1
    Write-Host "  uninstall Lumen -> $r"
}

if (-not $DryRun) { adb -s $Device shell input keyevent KEYCODE_HOME | Out-Null }
if ($DryRun) { Write-Host "Dry run only - nothing was changed." -ForegroundColor Yellow }
else { Write-Host "Restore complete. Reboot recommended: adb -s $Device reboot" -ForegroundColor Green }
