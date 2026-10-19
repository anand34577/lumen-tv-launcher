# Snapshot the TV's package/launcher/settings state so every change can be undone.
# Usage: .\backup.ps1 [-Device <ip:port>]
param([string]$Device)
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

$stamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$dir = Join-Path $PSScriptRoot "..\backups\$stamp"
New-Item -ItemType Directory -Force $dir | Out-Null
$dir = (Resolve-Path $dir).Path

function Save($name, $cmd) {
    adb -s $Device shell $cmd | Out-File -Encoding utf8 (Join-Path $dir $name)
}

Save "packages-all.txt"        "pm list packages -u"
Save "packages-enabled.txt"    "pm list packages -e"
Save "packages-disabled.txt"   "pm list packages -d"
Save "packages-thirdparty.txt" "pm list packages -3"
Save "packages-paths.txt"      "pm list packages -f -u"
Save "home-activity.txt"       "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
Save "home-role.txt"           "cmd role get-role-holders --user 0 android.app.role.HOME"
Save "settings-global.txt"     "settings list global"
Save "settings-secure.txt"     "settings list secure"
Save "settings-system.txt"     "settings list system"
Save "display.txt"             "wm size; wm density"
Save "getprop.txt"             "getprop"
Save "meminfo.txt"             "dumpsys meminfo"

@{
    device    = $Device
    created   = (Get-Date).ToString("s")
    model     = (adb -s $Device shell getprop ro.product.model).Trim()
    build     = (adb -s $Device shell getprop ro.build.fingerprint).Trim()
    homeRole  = (Get-Content (Join-Path $dir "home-role.txt") -Raw).Trim()
} | ConvertTo-Json | Out-File -Encoding utf8 (Join-Path $dir "manifest.json")

Write-Host "Backup saved to $dir" -ForegroundColor Green
$dir
