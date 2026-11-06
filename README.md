# ClearOS Ultra V5.0.0 RC2

Kişisel, çevrimdışı Android depolama yönetimi uygulaması. **Bu paket bir kaynak kodu sürüm adayıdır; gerçek Android APK derlemesi veya cihaz testi tamamlanmış değildir.**

## Bu pakette mevcut olanlar

- Beş sekmeli ana arayüz: Ana Sayfa, Temizlik, Medya, Analiz, Araçlar.
- SHA-256 ile doğrulanan kopya tespiti; risk sınıflandırması; eski APK, arşiv, büyük dosya ve erişilebilir medya taraması.
- Aynı depolama birimindeki `.ClearOS_Quarantine` klasöründe geri alınabilir karantina.
- Kullanıcı onayıyla kalıcı silme; dosya bazında ilerleme ve hata raporu; mevcut dosyanın üzerine yazmadan alternatif adla geri yükleme.
- Aynı klasördeki eski dosyaları değil yalnızca kullanıcı tarafından seçilmiş dosyaları işleme.
- Fotoğraf benzerliği ve olası bulanıklık incelemesi; **6.000 fotoğraflık hızlı** veya uyarıyla **20.000 fotoğraflık ayrıntılı** tarama.
- Fotoğraf sonuçlarında 50'şer öğelik sayfalama, manuel seçim; sıkıştırmada orijinaller korunur.
- Erişilebilen WhatsApp ve Telegram medyasının salt okunur analizi.
- Uygulama envanterinde 50'şer öğelik sayfalama.
- Depolama haritası, yerel takvime göre günlük 7/30 günlük kullanım grafikleri.
- Ekranı bloke etmeden arka planda karantina boyutunu yenileme; boş klasörlerin yanlışlıkla seçilememesi.
- Kullanıcı izinlerinin, Shizuku durumunun ve karantinanın **salt okunur tanılama ekranı**.
- Şarjdayken, pil tasarrufu kapalıyken haftalık tarama (silme yok), dışlanan klasörlerin korunması, yerel işlem günlüğü.
- İnternet izni, reklam, telemetri veya otomatik dosya silme yok.

## Önemli güvenlik noktaları

**Yalnızca test dosyalarında silme ve geri yükleme doğrulaması yapın.** Benzer fotoğraf tespiti hata yapabilir; uygulama önemli içerikleri kendiliğinden kaldırmaz. Karantinaya taşıma boş alan oluşturmaz, kalıcı silme geri alınamaz.

Paket kimliği `com.clearos.ultra`; karantina klasörü `.ClearOS_Quarantine` olarak korunmuştur. **Eski kurulumla farklı imzalanmış APK, üzerine güncelleme yapamaz.** Karantina kayıtları ve orijinal yollar yedeklenmeden mevcut APK'yı kaldırmayın.

## Doğrulama ve teslimat

- `bash tools/test-scanner.sh`: beş JVM birim/regresyon grubu ve Android Java sözdizimi ayrıştırması.
- `bash tools/stress-100k.sh 100000`: 100.000 boş boyutlu test dosyasıyla masaüstü dosya gezinme testi (fotoğraf/kopya karmaşık iş yükü değildir).
- `python3 tools/quality-check.py`: XML, manifest, CI ve temel gizlilik kontrolleri.
- `BUILD-APK.md`: GitHub Actions veya CircleCI ile APK derleme.
- `DEVICE-TEST-CHECKLIST.md`: telefonda güvenli doğrulama adımları.
- `ROADMAP-STATUS.md`: tamamlananlar, kısmen uygulananlar ve kalan işler.

JDK masaüstü testleri geçti, ancak bu çalışma ortamında **Android SDK ve Gradle yok; APK derlenemedi.** Media3, Shizuku, Android 11–16 cihaz testleri ve Material 3 bileşenlerine tam geçiş henüz doğrulanmadı/tamamlanmadı.
