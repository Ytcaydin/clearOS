# Telefonda test edilecekler — veri kaybı riskini en aza indirerek

Yalnızca **kendi oluşturduğun deneme dosyaları** üzerinde test yap. Daha önce V4 karantinayı boşaltmış olsan bile önce mevcut önemli verilerin yedeğini al.

1. **APK güncelleme:** Paket adı `com.clearos.ultra`; imza mevcut APK ile aynı değilse mevcut uygulamayı kaldırmadan önce karantina ve verileri güvenceye al. Farklı imza varsa mevcut kurulumu koru.
2. **Açılış/izin:** Android'in dosya izni ekranını kullan, izin reddinde uygulamanın hata vermeden çalıştığını doğrula. Ayarlar > Cihaz tanılama sayfasını kontrol et.
3. **Tarama:** Bir test klasöründe iki birebir kopya, bir eski APK ve bir önemli belge oluştur; önemli belge otomatik seçilmemeli.
4. **Karantina:** Sadece test dosyasını taşı; dosyanın orijinal konumda kaybolduğunu, karantinaya geçtiğini doğrula.
5. **Geri yükle:** Bir dosyayı asıl yerine geri getir. Aynı isimde başka dosya oluşturup alternatif adla geri yüklemeyi dene; mevcut dosyanın üzerine yazılmamalı.
6. **Kalıcı silme:** Sadece **test dosyalarında** çoklu seçim yap; x/y ilerlemesini, sonuçları, disk boş alan değişimini kontrol et. Önemli medyaları seçme.
7. **Medya:** 6.000 fotoğraf hızlı tarama varsayılan; 20.000 ayrıntılı modu yalnızca şarjdayken, yeterli pil ve boş alan varken dene. Yanlış benzerlikleri kontrol et; otomatik silme bekleme.
8. **Uygulama envanteri:** 50'şer sayfalama, boş durumda ve 150+ uygulamada liste tutarlılığı.
9. **Grafikler:** 7 ve 30 günlük görünüm; ilk gün yalnızca tek nokta varsa açıklama göstermeli. Yerel takvim sınırını doğrula.
10. **Ultra Mod:** Shizuku/Porter kurulmamışken normal mod çalışmalı. İzinli ADB temizliğini ancak sistemi yedekleyip anladıktan sonra dene.
11. **İzinler/güvenlik:** Bildirim izni reddedildiğinde JobScheduler arka taraması sessiz raporlasın. Korunan DCIM, Documents, Download dışlamalarını kontrol et.
12. **Dayanıklılık:** Tarama sırasında uygulamayı arka plana al, ekranı döndür, düşük pil modunu dene. Ani kapanma sonrasında karantina kayıtlarının tutarlı kaldığını kontrol et.
13. **Erişilebilirlik:** Büyük sistem yazı boyutu, TalkBack, küçük ekran, yatay/dikey mod ve durum çubuğu içeriğini kontrol et.
14. **Performans:** Büyük medya arşivlerinde pil/ısı ve tarama süresi gözlemi yap. Masaüstündeki 100.000 boş dosya testi gerçek cihaz yükünün yerini tutmaz.

Her testte beklenmeyen mesajı ve hangi adımda gerçekleştiğini kaydet. Dosyaların otomatik veya istemsiz silinmesi olursa kullanımı durdur.
