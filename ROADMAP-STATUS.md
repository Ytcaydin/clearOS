# V4.1 – V5 yol haritası: kanıta dayalı durum

| Hedef | Durum | Açıklama |
|---|---|---|
| Kalıcı silme ilerlemesi ve hata listesi | Kodlandı, masaüstü testi geçti | Android V4 silme gerçek cihazda kullanıcı tarafından doğrulandı; V5 RC2 henüz cihazda test edilmedi |
| Karantina seçme, filtreleme, geri yükleme | Kodlandı, masaüstü testi geçti | Dosya seçimi ve metadata denetimi mevcut; kalıcı silme dikkat gerektirir |
| 5 sekmeli düzen, depolama göstergesi | Kodlandı | Android V4 ekranlarında görülmüştü; V5 RC2 görünümü test edilmedi |
| Tam Material 3 bileşen dönüşümü | **Eksik** | Özel yerel Android görünümleri kullanılıyor; Material kütüphanesi dahil edilmedi |
| Erişilebilirlik/tema | Kısmen | Temel açıklamalar eklendi; TalkBack, yüksek kontrast, dinamik renk ve ışık teması test edilmedi |
| Benzer fotoğraflar, çoklu seçim | Kodlandı | Öneriler inceleme gerektirir, otomatik silme yok |
| 20.000 fotoğraf tarama, sayfalama | Kodlandı, cihazda test edilmedi | Hızlı varsayılan 6.000; geniş tarama yalnızca açık seçimle |
| Büyük medya önizlemesi/sıkıştırma | Kısmen | JPEG ve Media3 akışları var; kodlayıcı uyumu/kalite testi yapılmadı |
| Mesajlaşma medyası incelemesi | Kısmen | Erişilebilir dosyalar, sınırlı görsel önizleme; tüm medya ve güvenli silme akışı kapsam dışı |
| Klasör haritası, 7/30 günlük trend | Kodlandı | Gün sınırı yerel takvime düzeltildi; 7/30 günlük gerçek zamanlı veri birikimi gerek |
| Uygulama yöneticisi | Kodlandı, sayfalama eklendi | 50'şer listeleme, sistem ayarlarına güvenli geçiş; telefon doğrulaması gerek |
| Shizuku/Porter rehberi ve izin denetimi | Kodlandı, doğrulanmadı | Gerçek ADB komutları yalnızca onayla yürütülür |
| Şarjda haftalık tarama / bildirim | Kodlandı, gerçek cihazda doğrulanmadı | Android JobScheduler zamanlaması garanti edilemez; pil tasarrufunda atlar, otomatik silme yok |
| İşlem günlüğü ve cihaz tanılaması | Kodlandı | Tanılama salt okunurdur; gerçek cihaz hatalarını izlemede yardımcı |
| 100.000+ test dosyası | Masaüstünde 100.000 geçti | Yalnızca boş boyutlu dosya gezinmesi; gerçek 100k medya+hash iş yükü ve pil/ısı testi değil |
| APK ve 11–16 gerçek telefon testleri | **Eksik** | Çevrim dışı ortamda Android SDK ve Gradle yok; CI gerekli |

Bu kaynak paketi **V5 RC2** olarak etiketlenmiştir, kararlı V5 sürümü değildir.
