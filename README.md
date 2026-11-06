# ClearOS Ultra — V5.0.0 RC1 source candidate

Private, offline Android 11–16 storage analysis/cleaner. No advertisements, Internet permission, telemetry or unapproved file deletion. This is a **source release candidate, not a verified installed APK or a production-stable 5.0 release**.

## V5 changes implemented on top of V4

- **Quarantine:** per-file and percentage progress plus a visible progress bar during confirmed batch permanent deletion; verified reports continue to distinguish successful/failed deletes. Entries are reloaded after operations.
- **Restoration:** a separate explicit 'restore with different name on conflict' choice. It uses `Files.move` without overwrite, and preserves the existing destination file if a collision occurs.
- **Main dashboard and cleaning:** most recent scan date and candidate count, clarified difference between *findings* and *automatically recommended bytes*, avoids interpreting `0 B suggested` as `0 files found`.
- **Media tab:** live summary of large-video candidates and screenshots from the current scanner session, plus a direct large-file review shortcut. Counts show an unavailable state before scanning.
- **Analysis:** readable storage path labels (`Dahili depolama / ...`), paged folder list, 7/30-day trend range controls driven by locally stored daily history.
- **Tests:** fifth pure-Java regression group covering collision-safe restoration, progress and formatting.

## Previous V4 features retained

- Five-tab dark interface, SHA-256 duplicate checks, protected directories and single-volume `.ClearOS_Quarantine` folders.
- Reversible quarantine, opt-in permanent deletion, per-file error reporting and local logs.
- Visual review of similar/blurry photo candidates; media compression preserves originals; WhatsApp/Telegram accessible-media inventory.
- App inventory and advisory residue checks, opt-in Shizuku/Porter cache action, charging-only weekly scan and user-excluded folders.
- GitHub Actions and CircleCI build workflows.

## Safety and updates

**Always review what is selected before permanent deletion.** Quarantine itself consumes storage. Personal media, backups and system files must not be automatically removed. Clearing a folder based only on its filename is unsafe.

The app keeps package ID `com.clearos.ultra` and quarantine directory `.ClearOS_Quarantine`. Upgrading an installed APK requires the **same signing certificate**. If the new build is signed differently, do NOT uninstall the current app without preserving the quarantine files and original-path metadata first.

## Validation status

- Five portable JVM regression suites passed in this environment.
- Android Java source was parsed for syntax (NOT linked or compiled against Android SDK).
- Manifest and project files were checked for validity and absence of the `INTERNET` permission.
- **No Android SDK/Gradle is available here.** APK generation, Android 15/16 UI layout, device storage provider behavior, actual Shizuku authorization and Media3 encoder tests remain unverified.

A number of roadmap ideas are **not** completed simply by calling this package V5. Material 3 widgets, automatic identification of an entire media library, benchmarks on 100,000+ files, full automated Android UI tests, accessiblity audit, and hardware/power measurements require further development and Android testing. Treat this RC as a tested incremental improvement, not proof that every V4.1–V5.0 roadmap item is complete.

## Build

See `BUILD-APK.md`. GitHub Actions: push the root project directory into a repository, open **Actions → ClearOS Ultra 5.0 RC Android APK → Run workflow**, and retrieve `ClearOS-Ultra-v5-rc1-debug-apk` from artifacts if the build succeeds. CircleCI configuration is also included. Do not copy the ZIP wrapper directory as an extra repository level.

## Technical notes

- Android API 30–36, Kotlin not required, Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13.
- Runtime permissions and storage access depend on Android's policies. No generic bypass of apps' private data is claimed.
- Local source tests: `bash tools/test-scanner.sh`
- Additional QA: `python3 tools/quality-check.py`
