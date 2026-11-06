# Sürüm geçmişi

## 3.1.1 — Derleme düzeltmeleri (2026-11-06)

- **Kritik:** AIDL'de tüm metotlara açık işlem kodu (`trimCaches() = 1`); karışık kullanım derlemeyi durduruyordu.
- **Derleme:** Resmi Gradle 8.13 wrapper (`gradlew`, `gradle-wrapper.jar`) depoya eklendi; çalışma anında JAR indiren betik kaldırıldı.
- **CI:** GitHub Actions ile test + `assembleDebug`; APK artifact olarak yükleniyor.
- **Video:** Döndürme bilgisi okunuyor; dikey videolar kısa kenar 720 olacak şekilde ölçekleniyor (1080×1920 → 720×1280).

## 3.1.0 — Gerçek cihaz öncesi kritik düzeltmeler (2026-11-06)

- **Kritik:** Android/data dışındaki, kaynakla aynı depolama kökünde `.ClearOS_Quarantine/`; `.nomedia`, tarama dışı, birim bazlı kurtarma/kalıcı silme.
- **Kritik:** Shizuku `pm trim-caches 999G internal`; hedef boş alanın silinecek miktar olmadığı dikkate alındı.
- **Orta:** AIDL `destroy() = 16777114;`; uygulama depolama boyutunda çift sayılan cache kaldırıldı.
- **Orta:** Ana tarama, haftalık tarama ve fotoğraf taramasında hariç tutmalar bir kez okunan ortak immutable filtreye bağlandı.
- **Orta:** Seçim ekranı karantinaya taşımayı boş alan kazanımı gibi göstermiyor. Haftalık tarama sonuç bildirimi eklendi (izin varsa).
- **Küçük:** Fotoğraf bulanıklığında daha temkinli 160×160 + kontrast kontrolü; video 720p'den küçük girdileri büyütmüyor ve hedef bitrate var.
- **Küçük:** Launcher ve bildirim ikonları; Android 15+ kenar boşlukları; SHA-256 doğrulamalı Gradle wrapper bootstrap; CI düzenlemesi.
- **Test:** Üç JVM regresyon grubu + 18 Java sözdizimi + XML/YAML/Bash doğrulaması başarıyla tamamlandı. Android APK derlenmedi.

## 3.0.0 — Kişisel Ultra sürüm

- v1.1: Karantina, geri yükleme, toplu kalıcı silme için ayrı onay, klasör hariç tutma, risk profili
- v2.0: Shizuku/Porter Compatibility, sistem önbelleği işlemi, AppInsights ve uygulama artıkları
- v2.5: Algısal benzer fotoğraf, bulanıklık, medya sıkıştırma, WhatsApp/Telegram analizi
- v3.0: Depolama klasör haritası, kullanım trendi, şarjda haftalık arka plan taraması
- Tamamlanan test: Çevrimdışı Java tarama, karantina ve benzerlik mantığı
- *Android SDK ve internet bağımlılıkları bulunmadığı için gerçek APK derlemesi/telefon çalışması henüz doğrulanmadı.*
