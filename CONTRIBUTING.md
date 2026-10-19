# Contributing

Thanks for wanting to help. Lumen stays small on purpose, so a quick note on what fits.

**Welcome**
- Bug fixes, especially for TV models or firmware that behave differently
- Better D-pad navigation, focus handling and readability on large screens
- Translations (`app/src/main/res/values-<lang>/strings.xml`)
- Debloat notes for other TV models (as comments or a separate profile, not changes to the default)

**Please discuss first** (open an issue)
- New features or settings
- Anything that adds a permission

**Out of scope**
- Third-party libraries, analytics, crash reporting or anything that uses the network
- Ads, recommendations or content feeds

## Making a change

1. Fork the repository and create a branch: `feature/<name>` or `bugfix/<name>`.
2. Build and test on a TV or the Android TV emulator ([Building](docs/BUILDING.md)). Check that every
   new element can be reached and is clearly focused with the remote.
3. Match the existing style: plain Java, short focused methods, comments only where the *why* isn't
   obvious.
4. Open a pull request describing what changed, why, and how you tested it (device, Android version).
   Screenshots help for anything visual.

## Reporting a bug

Include the TV brand and model, Android version (Settings → About), Lumen version (App info), what you
did, what you expected, and what happened. If you can, attach the output of:

```bash
adb logcat -d | grep -iE "lumen|AndroidRuntime"
```
