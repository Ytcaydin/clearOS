# Sürüm Geçmişi

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
