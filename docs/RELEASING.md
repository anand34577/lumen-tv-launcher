# Releasing

Releases are built and published by GitHub Actions. Pushing a tag such as `v1.3.0` builds the APK,
signs it with the project key stored in repository secrets, verifies the signature, and attaches the
APK and its SHA-256 checksum to a new GitHub release. The release notes are taken from
`CHANGELOG.md`.

Steps 1–2 are needed once; steps 3–5 for every release.

## 1. Create a signing key

Skip this if you already have one. **Use the same key for every release, forever**: Android refuses
updates signed with a different key, and users would have to uninstall and lose their settings.

```bash
keytool -genkeypair -v \
  -keystore release.jks -alias lumen \
  -keyalg RSA -keysize 2048 -validity 36500
```

`keytool` comes with the JDK. Choose strong passwords and store the `.jks` file and passwords in a
password manager or another safe place outside the repository. If the key is lost, future releases
can't update existing installs.

## 2. Add the repository secrets

Encode the keystore as Base64:

```bash
# macOS / Linux
base64 -w0 release.jks > release.jks.b64        # macOS: base64 -i release.jks -o release.jks.b64
```

```powershell
# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Content -NoNewline release.jks.b64
```

In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The full contents of `release.jks.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_KEY_ALIAS` | The key alias (`lumen` in the example above) |
| `ANDROID_KEY_PASSWORD` | The key password (often the same as the keystore password) |

Delete `release.jks.b64` afterwards. Or set them from the command line with the GitHub CLI:

```bash
gh secret set ANDROID_KEYSTORE_BASE64 < release.jks.b64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

Secrets are never shown in logs, and workflows triggered by pull requests from forks can't read them.

## 3. Bump the version

In `LumenLauncher/app/build.gradle`:

```groovy
versionCode 4          // +1 every release; Android uses this to decide what's newer
versionName '1.3.0'    // shown to people, and must match the tag
```

Versions follow [semantic versioning](https://semver.org): new features raise the minor number, fixes
the patch number.

## 4. Update the changelog

Add a section at the top of `CHANGELOG.md`. The heading must contain the version number, because the
release notes are cut from it:

```markdown
## 1.3.0 — 2026-11-02

- What people will notice, in plain words
```

## 5. Tag and push

```bash
git commit -am "Release 1.3.0"
git tag -a v1.3.0 -m "1.3.0: short summary"
git push origin main v1.3.0
```

Follow the run under **Actions**. When it's green, the release appears under **Releases** with
`lumen-v1.3.0.apk` and `lumen-v1.3.0.apk.sha256`.

## When the release job fails

| Message | Cause and fix |
|---|---|
| `Missing repository secrets: ...` | One of the four secrets isn't set or is empty. Add it (step 2), then re-run the job from the Actions page |
| `Tag v1.3.0 does not match versionName 1.2.0` | The tag and `versionName` differ. Delete the tag (`git push --delete origin v1.3.0` and `git tag -d v1.3.0`), fix `build.gradle`, commit, and tag again |
| `Keystore was tampered with, or password was incorrect` | Wrong `ANDROID_KEYSTORE_PASSWORD`, or the Base64 value was truncated. Re-encode and paste the whole value |
| `Cannot recover key` / alias not found | Wrong `ANDROID_KEY_PASSWORD` or `ANDROID_KEY_ALIAS`. `keytool -list -keystore release.jks` shows the alias |
| `a release with the same tag name already exists` | That version was already published. Bump the version and tag again; don't reuse version numbers |

Builds on branches and pull requests don't need any secrets; they produce an unsigned APK as a build
check and upload it as a workflow artifact.
