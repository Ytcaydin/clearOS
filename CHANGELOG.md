# ClearOS Ultra development changelog

## 5.0.0 RC1 — derleme ve performans düzeltmeleri (2026-11-06)
- **Derleme:** v4.0 düzeltmeleri korunarak birleştirildi — AIDL açık işlem kodları, `setAllCaps` (ModernUi), MediaReviewActivity lambda adı, resmi Gradle 8.13 wrapper (JAR depoda).
- **Video:** Döndürme bilgisi dikkate alınıyor; dikey videolar kısa kenar 720 olacak şekilde ölçekleniyor.
- **Performans:** Ana sayfadaki karantina boyutu arka planda hesaplanıyor (UI thread'de FUSE okuması yok).
- **Performans:** Toplu geri yükleme ve yeni "çakışmada yeni isimle" seçeneği tek indeks anlık görüntüsüyle (`restoreBatch(items, rename, progress)`); önceden her dosya için iki tam indeks okuması (O(N²)).
- **Test:** BatchPurgeSelfTest toplu geri yükleme + yeni isimle geri yükleme senaryolarını kapsıyor.

## 5.0.0 RC1 — incremental source candidate (not a signed production release)

### Reliability and data safety
- Added `TrashStore.PurgeProgress`, raised after each file result, and `QuarantineManager` cross-volume progress aggregation.
- Quarantine Activity now updates a visible progress bar and displays `completed / total` during bulk deletion and restore.
- Added explicit optional rename-on-conflict recovery in quarantine. Original target is never intentionally replaced; desktop regression test covers this behavior.
- Existing batch delete confirmation, revalidation and detailed error records retained from V4.

### Information architecture and insights
- Dashboard now shows last completed scan timestamp and number of findings, separate from recommended bytes.
- Cleaning tab distinguishes raw candidates from auto-suggested file size.
- Media tab shows real *current scanner candidates* for large videos and screenshots and offers one-tap large-file filter.
- Analysis shows friendlier folder names, progressive 'show more' presentation, 7- and 30-day filters.
- Updated Android version code/name, GitHub Actions artifact name and regression tests.

### Verification and limitations
- Five JVM tests passed; Android source syntax parser passed. APK build and phone QA unverified here.
- Material 3 dependency migration, polished gallery previews for all media, full 100,000+ file performance benchmarking, battery profiling and Android instrumentation testing remain outstanding.

## Prior version history

- **4.0:** Five-tab dark dashboard, full-screen quarantine, bulk permanent deletion diagnostics, media review and local operation logs.
- **3.1:** Same-volume quarantine storage, cache command and Shizuku AIDL fixes, weekly notifications, cache/app-size accounting fixes.
- **3.0:** Initial broad analysis, compression and opt-in advanced-tool source integration.
