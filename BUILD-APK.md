# ClearOS Ultra V5.0.0 RC2 — APK oluşturma

Kaynak pakette APK bulunmaz. **Bu ortamda Android SDK ve Gradle olmadığı için APK burada derlenip doğrulanamadı.**

## GitHub Actions (önerilen)

1. Bu klasörün *içindekileri* bir GitHub deposunun ana dizinine yükle; `.github/workflows/android.yml` kökte olmalı.
2. GitHub → **Actions → ClearOS Ultra 5.0 RC2 Android APK** → **Run workflow** (veya repoya push yap).
3. Workflow JDK 17, Android API 36, Gradle 8.13 ile beş JVM testini, kalite denetimini, 10.000 dosyalık duman testini ve `assembleDebug` adımını çalıştırır.
4. Yeşil tamamlanırsa **Artifacts → ClearOS-Ultra-v5-rc2-debug-apk** arşivinden `app-debug.apk` dosyasını indir.
5. APK'nın kurulduğunu ve karantina işlemlerini yalnızca test dosyalarında doğrula.

## CircleCI

`.circleci/config.yml` kök dizinde bulunmalı. `build-apk` işini çalıştır; APK `app/build/outputs/apk/debug/` artefaktı altında olur.

## Yerelde

Android SDK Platform 36, JDK 17, Gradle 8.13 (veya ilk çalıştırmada kontrol edilen `./gradlew`) gerekir. 

```bash
bash tools/test-scanner.sh
python3 tools/quality-check.py
bash tools/stress-100k.sh 100000 # isteğe bağlı, büyük test
./gradlew --no-daemon --stacktrace assembleDebug
```

Gradle 8.13 ile Android Gradle Plugin 8.13.2 resmî uyumluluk gereksinimine uygundur; ancak projenin bağımlılıklarının **gerçek derleme uyumu henüz kanıtlanmadı**.

### İmza güvenliği

CI debug APK'sı farklı imzalanabilir. Android aynı paket adının farklı imzalı APK ile doğrudan güncellenmesine izin vermez. **Mevcut ClearOS uygulamasını sırf güncelleme yapmak için kaldırma**; önemli veriler ve orijinal-karantina metadata kayıtlarını koru. Mümkünse önce mevcut sürümü imzalayan anahtarla APK oluştur.
