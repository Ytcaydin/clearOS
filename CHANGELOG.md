# ClearOS Ultra 4.0.0

## V4.0 derleme ve performans düzeltmeleri (2026-11-06)
- **Derleme:** 3.1.1 düzeltmeleri yeniden uygulandı — AIDL açık işlem kodları, `setAllCaps` (ModernUi), resmi Gradle 8.13 wrapper (JAR depoda).
- **Video:** Döndürme bilgisi dikkate alınıyor; dikey videolar kısa kenar 720 olacak şekilde ölçekleniyor.
- **Performans:** Ana sayfadaki karantina boyutu artık UI thread'de okunmuyor (her render'da yüzlerce FUSE okuması / ANR riski).
- **Performans:** Toplu geri yükleme tek indeks anlık görüntüsüyle (`restoreBatch`); önceden O(N²) meta veri okuması.
- **Test:** BatchPurgeSelfTest toplu geri yükleme, sahte kayıt ve çakışma senaryolarıyla genişletildi.

## V3.2 — Product polish and quarantine integrity
- Full-screen selectable quarantine instead of 456-item AlertDialog.
- Detailed and batched permanent deletion with explicit failure reasons and post-operation reloading.
- Local on-device diagnostic log, explicit restore/delete confirmation, size and time sorting.
- Five-tab UI, modern native dark theme, new drawn navigation icons, circular storage usage view.
- Real scan categories, risk labels, suggestions and clear distinction between quarantine and actual disk recovery.

## V3.3 — Media and application review
- User-reviewed similar/blurry photos with thumbnails, paged results and reversible quarantine for individually selected items.
- Local WhatsApp/Telegram media type summary, accessible thumbnails and largest-file inventory.
- Original-preserving photo/video conversion retains before/after byte numbers and reports actual compression savings.
- Read-only app inventory, system settings hand-off, possible residue inspection without automatic deletion.

## V3.4 — Insights, guidance and automation
- Local storage usage line graph from prior daily measurements.
- Shizuku/Porter setup guide with permission, status, system cache prompt and explicit ADB operation.
- Weekly charging-only scan, notification permission, DCIM/Documents/Download exclusion controls.
- Operation log kept only on phone.

## V4.0 — Integration and quality
- V4 shell combines all five pages, restores earlier V3.1 scanner and vault data formats.
- Added GitHub Actions APK artifact workflow alongside CircleCI.
- Added batch deletion regression tests, XML checks and documented manual test criteria.
- No INTERNET permission, no unsafe background deletes, no automatic similar-photo deletes, no root assumptions.

## Not yet proven
- Android SDK build and install, Android 15/16 insets, Shizuku binder, media codec behavior, or actual FUSE file deletion. These require CI and real-phone tests.
