# Sürüm Geçmişi

## 5.0.0-rc2.3 — Taramalar en yüksek hıza (2026-11-06)
- **Ana tarama:** Dosya başına tek `lstat` (önceden symlink/klasör/dosya/boyut/tarih için 5 ayrı FUSE çağrısı); alt klasör yolları birleştirmeyle (klasör başına 3 `getCanonicalPath` kalktı); klasör listeleme ve kopya dosya özetleri (örnek + SHA-256) 2–6 iş parçacığında paralel. Eski tarayıcıyla 37.886 dosyalık ağaçta sonuçlar birebir aynı.
- **Fotoğraf dedektifi:** Fotoğraf listesi depolamayı gezmek yerine Android medya dizininden (MediaStore) tek sorguyla, en yeniden eskiye; boyutlar dizinden geldiği için başlık okuma atlanıyor; 6 iş parçacığına kadar paralel çözümleme, bir grup çözülürken sonraki hazırlanıyor. Medya dizini yoksa eski klasör taraması devreye girer.
- Not: `.nomedia` içeren klasörlerdeki fotoğraflar (ör. WhatsApp "Sent") medya dizininde olmadığı için fotoğraf dedektifinde yer almaz; ana tarama bu klasörleri yine kapsar.

## 5.0.0-rc2.2 — Fotoğraf dedektifi hızlandırma (2026-11-06)
- **Hız:** Fotoğraflar 60'lık gruplar halinde paralel (çekirdek sayısı − 1, en fazla 4 iş parçacığı) çözülüyor; sonuçlar tarama sırasıyla birleştiriliyor, çıktı değişmiyor.
- **Hız:** Bulanıklık ve benzerlik ölçümü `getPixel` yerine tek `getPixels` okumasıyla (fotoğraf başına ~130 bin JNI çağrısı → 2).
- **Arayüz:** Butonlara 44 dp en küçük yükseklik; "Taramayı durdur" ve fotoğraf sayfası butonları artık ince görünmüyor.

## 5.0.0-rc2.1 — Önbellek işlemleri geri bildirimi (2026-11-06)
- **ADB önbelleğini daralt:** "Çalıştır"dan sonra anında ilerleme penceresi (servis başlatılıyor → önbellekler daraltılıyor); 20 sn bağlantı ve 2 dk toplam zaman aşımı; servis kapanırsa hata mesajı. Önceden servis bağlanamazsa ekran sessiz kalıyordu.
- **Sonuç:** Öncesi/sonrası boş alan farkı gösteriliyor ("Açılan alan: yaklaşık X"); `pm` mutlak yolla çalıştırılıyor.
- **Android sistem ekranı:** Dönüş sonucu artık işleniyor; ekran iptal edilir ya da telefon göstermezse kullanıcıya açıklama çıkıyor.
- Her iki işlem de işlem günlüğüne yazılıyor.

## Sabit imza — doğrudan güncelleme (2026-11-06)
- CI artık her derlemede rastgele değil, `CLEAROS_DEBUG_KEYSTORE` GitHub secret'ındaki sabit anahtarla imzalıyor; yeni APK eskisinin üzerine **güncelleme** olarak kurulur, ayarlar korunur.
- Secret yoksa derleme yine çalışır ama uyarı verir (rastgele anahtar).
- İş akışı APK sertifikasının SHA-256 özetini yazdırır.

## V5.0.0 RC2 — birleştirme notu (2026-11-06)
- RC2, düzeltilmiş `v5.0.0-rc1` dalı üzerine birleştirildi; AIDL işlem kodları, `setAllCaps`, lambda adı, dikey video ölçekleme, Gradle wrapper ve toplu geri yükleme (`restoreBatch`) korunuyor.
- Ana sayfa karantina boyutu: RC2'nin `io` executor'ındaki eşdeğer kodu yerine ayrı arka plan iş parçacığı korundu (tarama sürerken de güncellenir, ilk açılışta "-1 B" yerine "…" gösterir).

## V5.0.0 RC2 — 6 Kasım 2026

- Fotoğraf dedektifine kullanıcı kontrollü 6.000 / 20.000 resim tarama profilleri ve canlı tarama sayacı eklendi.
- 20.000 sonuçla sayfa sayısı sınırlı tutulması için fotoğraflar 50'şer parça gösteriliyor.
- Uygulama envanterindeki 150 öğelik sınır kaldırıldı; 50'şer sayfalı tarayıcı eklendi.
- Salt okunur cihaz tanılama ekranı (dosya izni, kullanım izni, Shizuku, karantina envanteri) eklendi.
- Depolama geçmişi yerel takvim günüyle tutulacak şekilde düzeltildi.
- Pil tasarruf modunda haftalık taramayı başlatmama ve durdurulan JobService için çift bitiş bildirimi yapmama koruması eklendi.
- Ana ekranda karantina listesi artık UI thread üzerinde okunmuyor. Taşıma sonrası özet arka planda yenileniyor.
- Boş klasörleri karantinaya taşıma desteklenmediği için bu adayların seçimi engellendi; açıklama eklendi.
- Gezinti ve işlem kartları için erişilebilirlik açıklamaları eklendi.
- 100.000 dosyalık (sıfır bayt test nesnesi) stres testi eklendi ve yerel ortamda çalıştırıldı.
- GitHub Actions ve CircleCI iş akışlarına XML/gizlilik denetimi ve 10.000 dosyalık duman testi eklendi.
- Sürüm kodu 51'e yükseltildi, sürüm adı `5.0.0-rc2` yapıldı.

## RC1 ve V4'ten taşınan özellikler

- Silmede dosya bazında ilerleme ve sonuç raporu; karantina geri yükleme çakışma koruması.
- Sekmeli tasarım, depolama halkası, kategori seçimi, büyük dosyalar, 7/30 günlük depolama grafikleri.
- Fotoğraf/video sıkıştırma, WhatsApp/Telegram analizi, haftalık şarjda tarama, Shizuku rehberi.

**Doğrulanmayan işler:** Android SDK ile derleme, gerçek telefonda v5 RC2 kurulumu, Material 3 kütüphane geçişi, 11–16 arasındaki bütün cihaz davranışları, 20.000 fotoğrafın donanım/pil etkileri, Shizuku ve video kodlayıcı testleri.
