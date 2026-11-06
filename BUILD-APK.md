# Build ClearOS Ultra 5.0 RC1 APK

An APK is **not** included in this ZIP. Android Gradle Plugin needs Android SDK packages, JDK 17 and network access to retrieve dependencies. This assistant environment does not have an Android SDK available.

## GitHub Actions

1. Upload the *contents* of this project directory to a private GitHub repository. The `.github/workflows/android.yml` file must appear at the repository root.
2. Open **Actions → ClearOS Ultra 5.0 RC Android APK → Run workflow** (or push to trigger a run).
3. A successful build runs five JVM test suites and `gradle --no-daemon --stacktrace assembleDebug`.
4. Download the `ClearOS-Ultra-v5-rc1-debug-apk` artifact. The Android package will be `app-debug.apk`.

## CircleCI

Use `.circleci/config.yml` and the workflow's `build-apk` job. A successful build publishes `app/build/outputs/apk/debug` under the artifacts tab.

## Local build with installed tools

- Android SDK Platform 36 and Build Tools 36.0.0
- Gradle 8.13, Android Gradle Plugin 8.13.2, JDK 17
- Run `bash tools/test-scanner.sh` and `gradle assembleDebug`.

**Certificate warning:** Android only accepts updates signed by the same signing certificate. CI debug APKs may use different certificates than existing builds. Do not uninstall an existing app if its quarantine data has not been backed up (including the metadata under `.ClearOS_Quarantine/index`).

## Verification after installation

1. Grant file access permission and scan a small test folder.
2. Select a disposable test file, quarantine it, then restore it.
3. Create a destination name collision and use 'Çakışmada yeni isimle' to ensure both files survive.
4. Quarantine several disposable files and test bulk deletion; verify the visual percentage and final counts.
5. Confirm 7/30-day view, readable folder names, exclusion settings and permission prompts.
6. Test Shizuku separately only after installing/authorizing its service. Confirm Media3 transcoding only with disposable input files.
7. Test screen insets, screen rotation, background/low memory interruptions and accessibility, then record unexpected behavior.

Do not represent parse-only validation as a successful Android build.
