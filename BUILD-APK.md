# Build ClearOS Ultra 4.0 APK

## Recommended: GitHub Actions
1. Extract `ClearOS-Ultra-v4.0.0-source.zip` on your computer. Upload **its contents** to the repository root (the repository root must contain `settings.gradle`, `app/`, `.github/`, etc.). Hidden folders like `.github` must be preserved.
2. In your GitHub repository, go to **Actions** -> **ClearOS Ultra 4.0 Android APK** -> **Run workflow** (or commit/push to trigger the workflow).
3. When the build succeeds, open the workflow run and download **ClearOS-Ultra-v4-debug-apk** under *Artifacts*.
4. Extract the artifact ZIP to obtain **app-debug.apk**. Android may require a one-time authorization for installing an APK from your browser / file manager.
5. Check the certificate before trying to update an installed V3.1 app. Different CI debug keys cause `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; a signing mismatch is not a code bug. Keep a backup of `.ClearOS_Quarantine` before any migration.

## CircleCI alternative
- Connect a repository containing the source tree to CircleCI.
- `.circleci/config.yml` runs core tests, installs API 36 / build-tools, and tries `./gradlew --no-daemon assembleDebug`.
- Download `app/build/outputs/apk/debug/app-debug.apk` from CircleCI artifacts on success.

## Local build
You need Android SDK 36, JDK 17, and Gradle 8.13. Android Gradle Plugin is pinned to 8.13.2.

```sh
bash tools/test-scanner.sh
gradle --no-daemon assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

`./gradlew` is an existing SHA-256 checked bootstrap script and may need network access on its first run. GitHub Actions uses the official Gradle setup action directly to avoid depending on that download route.

## Android phone test after build
- Open V4; observe storage and all five tabs.
- Run a scan; check that excluded folders are respected and that categories list individual files.
- Check that previous 3.1 quarantine files are still visible.
- Restore **one unimportant file**, verify its original path.
- Select **one disposable quarantined file** and confirm permanent delete; check list reload and actual file existence / device free space.
- On any failure, open **Araçlar -> İşlem günlüğü** and record error. Do NOT use bulk delete until one-file recovery and deletion work.
- Check media compression on small test files, photo selection, Shizuku permissions and charging-only scheduled scan on the phone.

## Limitations
No APK was built in the current development environment. Syntax/JVM test passes are not a substitute for Android API linking or physical Android tests. Source upload and workflow build results determine actual APK readiness.
