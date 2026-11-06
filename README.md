# ClearOS Ultra 4.0 — Private Android Cleaner

**Personal Android 11–16 cleaner. No ads, analytics, Internet permission, root, or unattended file deletion.**

> **Build status:** Source code and JVM core tests provided. The APK has NOT been compiled in this environment, since Android SDK and Gradle are not installed and downloading packages is blocked. Android-specific compilation and real-device QA must occur through GitHub Actions / CircleCI and your phone. Do not interpret source syntax checks as proof that the APK builds or all features work.

## What changed in V4

### Five-tab professional experience
- **Home:** real-time storage ring, last scan recommendations, quarantine size, scan start/stop.
- **Cleaning:** real scanner results, type filters, risk labels, manual selection, secure quarantine and duplicate-original protection.
- **Media:** conservative similar/blurry photo detection, manual review and selecting to quarantine, photo preview, original-preserving photo/video compression with before/after savings, WhatsApp/Telegram accessible-media summary and preview.
- **Analysis:** top folder groups from the last scan, used/free overview and a line chart using locally stored daily samples.
- **Tools:** full-screen Quarantine, Shizuku wizard, cache management, app insights, possible orphan Android/media folders, background schedule, exclusions and local operation log.

### Quarantine and permanent deletion improvements
- `QuarantineManager.purgeBatch()` groups by storage volume; each `TrashStore` indexes its entries once per batch instead of rescanning for every item.
- The payload is rechecked, symlinks are rejected, and deletion uses `Files.delete()` so platform file failures can be reported instead of silently swallowed.
- Files are **permanently deleted only after selecting them and confirming in a separate dialog**.
- Full-screen Quarantine includes bulk select, size filter, sorting, restore, delete, a progress/status line and per-file failure reasons.
- Results distinguish number deleted, total byte size of deleted payloads and reported primary-storage free-space change (which may differ owing to filesystem accounting / SD cards).
- After a batch, the list is reloaded from disk; failed items can remain for inspection.
- Action logs remain on device and may contain a failed file's name (not contents).

### Existing features retained
- SHA-256 exact duplicate verification, time/size revalidation, symlink avoidance, protection of Android/data, OBB, and quarantine path.
- Photo perceptual similarity and deliberately conservative blur heuristics; predictions never authorize automatic deletion.
- Media3 conversion at max 720p (hardware support varies), optional Shizuku/Porter permission for `pm trim-caches` and Android's system-managed external-cache approval screen.
- Scheduled **charging-only** weekly scan, Android notification (if permitted), exclude DCIM/Documents/Downloads, local storage history.

## Security rules
1. **Do not clear the quarantine blindly:** you previously had 456 files / 205.7 MB there. Review and restore anything important before permanent deletion.
2. The quarantine is `/.ClearOS_Quarantine/` on each storage volume, alongside the scanned files, with `.nomedia`; it is NOT inside app-private Android/data.
3. Quarantine itself does not free storage. Deleting a payload frees its allocation; the displayed file sizes and filesystem-reported free space may not match exactly.
4. Any Android FUSE/permission failure is reported. A successful JVM test does not guarantee that Android allows every deletion.
5. App residue suggestions are **advisory** only; the app will not remove system packages or third-party private data.
6. No arbitrary shell execution interface. Shizuku is optional and must be independently installed, started and authorized by the user.

## Scope and known limits
- Android 11+ does not grant universal access to all other apps' private data; the application scans eligible shared storage only.
- Image analysis examines up to 6,000 eligible photos per scan and uses a heuristic, not AI certainty.
- Messenger media is read-only in that section. Up to 24 image previews and 100 large-media entries appear from permitted paths.
- App insight listing shows up to 150 installed apps in the V4 UI; usage/storage stats may be unavailable without special app-usage access.
- Video encoding is device/codec dependent and can fail. An original is never deleted just because compression was requested.
- Navigation and dialogs use a self-contained native design system styled like modern Material interfaces; this is not a dependency on Google's full Material 3 widget library.
- There is **no root mode** or unsupported Android private-cache deletion guarantee.

## Build APK
Read **BUILD-APK.md**. GitHub Actions and CircleCI configurations are bundled. Requirements: SDK 36, JDK 17, Gradle 8.13, AGP 8.13.2. Run `bash tools/test-scanner.sh` and then `gradle assembleDebug` (or verified `./gradlew assembleDebug`) in an SDK-equipped environment.

**Update signing caveat:** To upgrade an installed 3.1 APK, V4 must be signed with the same signing certificate. CI systems often generate different DEBUG certificates. If Android rejects the update, *do not immediately uninstall*: ensure the entire `.ClearOS_Quarantine` is backed up, check that your source files and restoration metadata are preserved, and plan the migration. A different certificate cannot install as an update over the existing app.

## Documentation
- `CHANGELOG.md` changes
- `BUILD-APK.md` GitHub Actions and CircleCI
- `TEST-RESULTS.txt` verified tests and missing checks
- `tools/test-scanner.sh` JVM regression tests
- `.github/workflows/android.yml` repeatable GitHub APK artifact

Licensed for personal use. The project keeps the existing package name `com.clearos.ultra` and the `.ClearOS_Quarantine` directory convention.
