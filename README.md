# ClearOS Ultra 3.1.0 — Kişisel Android Temizleyici

**Durum:** Uygulanmış kaynak proje / *Android APK derlemesi ve gerçek cihaz testi henüz doğrulanmadı.* Bu bir APK dosyası **değildir**. Telefonuna kurmak için alttaki CircleCI adımlarıyla `app-debug.apk` derlenmelidir.

## Temel yaklaşım

Tamamen yerel, reklamsız, internete veri göndermeyen, root gerektirmeyen Android 11–16 uygulaması. Paylaşılan depolamayı tarar; hiçbir kişisel dosyayı kendiliğinden silmez. Kullanıcı onayının yanı sıra *karantina*, değişmiş dosyaya dokunmama ve korunan orijinal doğrulaması kullanır.

### v1.1 — Güvenli Temizlik

- SHA-256 ile **kesin kopyalar** (önce boyut + örnek hash) ve `DCIM/Camera` orijinalini koruma.
- Geçici dosya, APK/APKS/XAPK/APKM, eski arşiv, büyük dosya, küçük resim, ekran görüntüsü ve boş klasör adayları.
- **Karantinaya taşı / tek tek geri yükle / ayrı onayla kalıcı sil.** Toplu karantina boşaltma ayrıca iki kez onay ister.
- **v3.1 karantina düzeltmesi:** Dosyalar uygulamanın `Android/data` dizinine değil, kaynakla **aynı paylaşılan depolama birimi** içindeki `.ClearOS_Quarantine/` dizinine taşınır. İçeride `.nomedia` oluşturulur; tarayıcı bu dizini kesinlikle atlar. Uygulama silinse bile karantina içindeki dosyalar kalabilir.
- Karantinaya taşımak **yer açmaz**. Aynı hacimde atomik ad değiştirme mümkün olmazsa işlemi durdurup orijinali korur; Android/ROM/SD kart farklılıklarında işlemin başarılı olacağı garanti değildir. Boş klasörler karantinaya taşınmaz. Kullanıcılar gizli karantina dizinini elle silmemelidir.
- Tarama işleminde symlink'leri atlama, gizli sistem dizinlerine dokunmama, tarama sonrası dosya boyutu/zamanı tekrar kontrolü, yinelenen dosyayı SHA-256 ile yeniden doğrulama.
- `DCIM`, `Documents`, `Download` dizinlerini tercihe bağlı hariç tutma.

### v2.0 — Ultra Mod ve uygulama analizi

- `MANAGE_EXTERNAL_STORAGE`: Android'in izin verdiği **paylaşılan depolama**; diğer uygulamaların özel verilerini açmaz.
- Sistem onaylı harici uygulama önbelleği temizleme ekranı.
- **Shizuku / Porter Compatibility** üzerinden gerçek kullanıcı onaylı ADB UserService ile sadece `pm trim-caches 999G internal` çalıştırma. İşlem sonucu cihaz/ROM'a göre değişir; uygulama veritabanlarını silmeye veya sistem uygulamalarını kaldırmaya çalışmaz.
- Kurulu/kullanılmayan uygulamalar; *Kullanım erişimi* izni açıksa son kullanım ve `StorageStatsManager` ile boyut görüntüsü. Kaldırma için yalnızca Android sistem uygulama ayrıntılarına geçer.
- Android/media altında olası kaldırılmış uygulama klasörleri (yalnızca inceleme; yanlış pozitif olabilir).

### v2.5 — Medya

- Fotoğraflar için düşük çözünürlüklü **dHash** algısıyla görsel benzerlik (en çok 6.000 fotoğraf), deneysel bulanıklık önerileri (160×160 örnek, daha temkinli eşik ve kontrast koruması). Yanlış pozitif olabileceğinden **otomatik silinmez**; kullanıcı önizleyip karantinaya taşıyabilir.
- **JPEG sıkıştırma:** kalite 82, uzun kenar en çok 2560 px; şeffaf resimler reddedilir, dönüşüm yalnızca daha küçükse `Pictures/ClearOS Ultra` altına yeni dosya kaydeder. Orijinal değişmez; fotoğraf EXIF/meta verilerinin bazıları korunmayabilir.
- **Video dönüştürme:** Media3 Transformer, H.264 en fazla kaynak çözünürlüğü / 720p, uyarlanmış hedef bit hızı; başarıyla dönüştürülüp daha küçükse `Movies/ClearOS Ultra` altına ayrı kopya kaydeder. Telefonun donanım/kodlayıcı desteği gerekir, bazı videolar dönüştürülemeyebilir. Orijinal **hiçbir zaman otomatik silinmez**.
- WhatsApp/Telegram'ın erişilebilir ortak medya dosyalarını fotoğraf/video/ses/belge olarak ölçme, büyüklerini inceleyip tek tek karantinaya taşıma. Sohbet veritabanlarına ve özel uygulama depolamasına dokunmaz.

### v3.0 — Analiz, raporlama ve otomasyon

- **Klasör bazlı depolama haritası**: tarama sırasında toplanan boyutlar ve görsel çubuklar.
- **Depolama trendi:** günlük kullanım boyutu kaydı (son bir yıl), önceki tarihe göre artış/azalış.
- **Akıllı risk etiketi**: düşük risk / incele / önemli dosya olabilir; malware tarayıcısı değildir.
- **Haftada bir otomatik tarama:** yalnızca şarjda, pil seviyesi yeterliyken Android `JobScheduler` ile; *asla otomatik silme yok*. Kullanıcının hariç tuttuğu klasörleri dikkate alır; bildirim izni varsa sonuç bildirimi gösterir, verilmemişse uygulamadaki özet ekranına kaydeder. Sistem zamanlamayı erteleyebilir.
- Tarama sonucu kategori filtreleri, önerilen adayları seçme, izinsiz klasör sayacı ve aşırı tarama sınırları.

## Kurulum — CircleCI

1. Bu ZIP'i çıkar; içindeki `ClearOS-Ultra-v3.1.0/` klasörünün içeriğini GitHub **özel** deposunun köküne yükle. `.circleci/config.yml` kökte olmalı.
2. GitHub deposunu CircleCI'da bağla ve bir pipeline başlat.
3. `Java güvenlik ve regresyon testleri` ve `Debug APK - SHA-256 doğrulamalı Gradle wrapper` tamamlanınca **Artifacts → clearos-ultra-apk → app-debug.apk** dosyasını indir.
4. Telefonunda APK'ya dokunup yükle. Uygulamaya *Tüm dosyalara erişim* iznini ayarlardan ver. Kendi onayın dışında hiçbir dosya kalıcı silinmez.
5. Ultra Mod'u kullanmak istersen Shizuku'yu başlat veya Porter + **Porter Compatibility** kur ve uygulamaya Shizuku izni ver. Bu isteğe bağlıdır.

Gradle: **8.13** (SHA-256 doğrulamalı, ilk çevrimiçi çalıştırmada Gradle'ın orijinal wrapper JAR dosyasını edinen `./gradlew` betiği), Android Gradle Plugin: **8.13.2**, Java: **17**, `compileSdk`/`targetSdk`: **36**, minimum API: **30**. Media3: **1.9.4**, Shizuku: **13.1.5**. Proje internet izni istemez; APK derleyici bağımlılıkları indirmek için internete ihtiyaç duyar.

## Test ve durum

Geliştirme ortamında saf Java testleri çalıştırıldı:

```bash
./tools/test-scanner.sh
```

Testlenen: SHA-256 kopya eşleşmesi, kamera orijinalini koruma, korunmuş Android/data dizinlerini atlama, APK/geçici dosya sınıfları, taramadan sonra değişen dosyayı reddetme; v3 karantinaya taşıma, geri yükleme, kalıcı boşaltma, sahte kayıt reddi, klasör boyutu toplama, dHash gruplayıcı; **v3.1 iki ayrı depolama kökünde karantina, `.nomedia`, tekrar taranmama, hariç tutma ayarlarının saf Java mantığı**.

**Henüz doğrulanmayanlar:** Android üzerinde **APK derlemesi**, gerçek telefon ve farklı mount/FUSE birimlerinde `renameTo` davranışı, Android 13+ bildirim izni akışı, gerçek telefon UI davranışı, ADB/Shizuku/Porter bağlantısı ve izin akışı, Media3 video dönüştürme, farklı üreticilerin Android 16 depolama politikaları, SD kart desteği ve uzun süreli performans. Bunlar cihaz/CI üzerinde test edilmeden hazır ve kusursuz APK olarak sunulamaz.

## Güvenlik sınırları

- Android 11+ sistem korumaları nedeniyle `/Android/data`, `/Android/obb` ve `/data/data` gibi başka uygulamaların özel dizinlerine doğrudan erişim yoktur. Shizuku ADB yetkisi bile her korumayı kaldırmaz.
- Benzer fotoğraf ve olası bulanıklık *tahmindir*; yanlış eşleşme olabilir. Tek tek görsel doğrulama gerekir.
- Karantina içinde tutulma **boş alan açmaz**; içindeki dosyaları kalıcı silmeden önce mutlaka geri yükleme ihtiyacını değerlendir.
- Sistem önbellekleri sonradan yeniden oluşturulabilir. Sahte RAM/hızlandırma iddiaları yoktur.
- `QUERY_ALL_PACKAGES` uygulama envanteri için, `PACKAGE_USAGE_STATS` ise açık kullanıcı izninden sonra kullanım geçmişi için istenir. Google Play politikaları hedeflenmemiştir; kişisel yükleme içindir.
- İnternet, reklam, analitik, kullanıcı hesabı ve otomatik kalıcı silme yoktur.

## Mimari

| Dosya | Görev |
|---|---|
| `SafeScanner.java` | Sınıflandırma, kesin SHA-256 kopyalar, güvenlik doğrulaması, depolama grupları |
| `TrashStore.java` / `QuarantineManager.java` | Depolama birimi başına geri alınabilir karantina + kurtarma + kalıcı silme |
| `SimilarityIndex.java` / `ImageInspector.java` | dHash fotoğraf gruplayıcı, bulanıklık adayı |
| `MediaCompressor.java` | Kullanıcı onaylı yerel JPEG/Media3 dönüşümü |
| `MessengerInspector.java` | Ortak medya analizi |
| `AppInsights.java` / `AppResidueInspector.java` | Kurulu uygulamalar, kalan ortak klasörler |
| `UltraAccess.java` / `UltraUserService.java` | Shizuku ADB UserService ile kısıtlı önbellek komutu |
| `BackgroundScanService.java` / `StorageHistory.java` / `ExcludedPaths.java` | Şarjda haftalık rapor + bildirim, ortak hariç tutmalar, depolama trendi |
| `RiskEngine.java` | Kullanıcıya açıklanabilir risk etiketleri |
| `MainActivity.java` / `UltraFeaturesActivity.java` | Tarama ve Ultra araçlar ekranları |
| `gradlew` / `.circleci/config.yml` | Checksum doğrulamalı Gradle wrapper bootstrap, APK derleme ve artefakt teslimi |

## 3.1 kritik düzeltmeleri ve güvenlik notları

- Gizli karantina klasörü artık her kaynağın kendi paylaşılan depolama kökündedir. Uygulama silinse dahi karantina otomatik temizlenmeyebilir. **Geri yükleme için içindeki `index/` ve `items/` dizinlerini birlikte sakla.**
- `pm trim-caches 999G` önbellek temizlemeye çalışır; Android uygulama özel verilerini sıfırlamaz ve her üretici bunu aynı biçimde desteklemeyebilir. Gerçekten kaç MB temizlendiği garanti edilmez.
- 160×160 bulanıklık tahmini hâlâ yanılabilir. Tek başına karar vermek için kullanma.
- Gradle wrapper JAR dosyası kaynak ZIP içinde bulunmaz; **ilk çevrimiçi `./gradlew`** komutu 8.13 sürümünün resmî JAR'ını indirir ve yayımlanmış SHA-256 imzasıyla doğrular. Gradle dağıtım ZIP'i de `distributionSha256Sum` ile doğrulanır. Bu işlem bir kez çevrimiçi çalışmayı gerektirir.
- v3.1'in saf Java testleri başarıyla koşmuştur; Android SDK olmadan uygulamanın tüm Java/Kotlin kaynaklarını derleyemedik. `app-debug.apk` elde etmek için CircleCI zorunludur.
