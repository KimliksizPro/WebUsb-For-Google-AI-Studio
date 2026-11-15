# WebUSB Bridge

**WebUSB Bridge**, Android cihazlarda yerel `adbd` TCP portunu (`127.0.0.1:5555`) bir localhost WebSocket sunucusuna (`ws://127.0.0.1:8765`) bağlayan ve Chrome / Brave gibi harici tarayıcıların **WebUSB (`navigator.usb`)** üzerinden kendi cihazına veya localhost ADB oturumuna erişmesini sağlayan hafif, minimal bir Android köprü uygulamasıdır.

Bu proje özellikle **Google AI Studio Build** ortamında geliştirilmek, düzenlenmek ve doğrudan APK/GitHub akışıyla dağıtılmak üzere tasarlanmıştır.

---

## Temel Özellikler

- **WebSocket ↔ ADB TCP Paketleyici (`AdbPacketizerEngine`)**
  - Tarayıcıdan gelen `transferOut` ikili çerçevelerini doğrudan yerel ADB TCP soketine yazar.
  - ADB TCP akışından gelen yanıtları `DataInputStream.readFully` mantığıyla **24 bayt ADB başlığı** ve **Little-Endian `data_len` gövde** olarak iki ayrı Binary WebSocket çerçevesine böler (`ya-webadb` / `Tango` / `WebADB` uyumlu).
- **WebUSB v2 Bookmarklet & Polyfill Üretici (`WebUsbJsGenerator`)**
  - `navigator.usb` arayüzünü `VID: 0x18d1 / PID: 0x4ee7` ve standart ADB arayüz sınıfıyla (`255 / 66 / 1`) taklit eder.
  - Köprü portu değiştirildiğinde (`8765` veya özel port) Bookmarklet ve ham JS kodunu dinamik olarak günceller.
- **Root & Manuel ADB Port Kontrolü (`AdbPortManager`)**
  - Root (`su`) erişimi varsa tek dokunuşla ADB TCP portunu açıp kapatır.
  - Root yoksa mevcut açık ADB portunu (`5555`, `5556` veya özel port) canlı TCP yoklamasıyla takip eder.
- **Kalıcı Ön Plan Servisi (`WebUsbBridgeService`)**
  - Chrome veya Brave'e geçildiğinde Android'in yerel WebSocket sunucusunu kapatmasını önler.
  - Bildirim üzerinden tek dokunuşla **Durdur** veya **Komple Kapat** (servis + Root ADB) desteği sunar.
- **Minimal 3 Sekmeli Siyah-Kırmızı / Beyaz-Kırmızı Arayüz**
  1. **Kontrol**: WebSocket ve ADB port durumu, Köprü Başlat/Durdur, Komple Kapat, Root ADB aç/kapat, port ayarları ve tek tık Bookmarklet kopyalama.
  2. **Tarayıcı Kodu**: WebUSB v2 Bookmarklet / Ham JS kopyalama, hızlı servis kontrolü ve 3 adımlı kurulum rehberi.
  3. **Kayıtlar**: Filtrelenebilir (`Tümü`, `Sistem & Root`, `Trafik`) canlı paket ve sistem günlüğü.

---

## Kısa Kurulum (Chrome / Brave)

1. **Köprü ve ADB'yi Başlatın**: **Kontrol** sekmesinden ADB portunu (Root ile veya kablosuz hata ayıklama/USB üzerinden `5555`) açın ve **Köprüyü Başlat** butonuna dokunun.
2. **Bookmarklet Ekleyin**: **Tarayıcı Kodu** sekmesinden **Bookmarklet Kodunu Kopyala** butonuna dokunun; Chrome veya Brave'de yeni bir yer imi oluşturup URL alanına yapıştırın.
3. **WebADB'de Çalıştırın**: `app.webadb.com` veya `tangoapp.dev` gibi bir WebADB aracını açın, adres çubuğuna yer iminizin adını yazıp çalıştırın. **`WebUSB v2 Aktif!`** uyarısını gördükten sonra cihazı bağlayın.

---

## Google AI Studio Uyumluluğu

Bu repo, **Google AI Studio Build** Android çalışma zamanı ile tam uyumlu olacak şekilde yapılandırılmıştır:

- **`metadata.json`**: AI Studio proje kimliği, açıklaması ve `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` alanı korunur.
- **`app/build.gradle.kts`**:
  - AI Studio imza yapılandırmaları (`release` / `debugConfig` ve `debug.keystore.base64`) korunur.
  - Secrets Gradle Plugin (`.env` / `.env.example`) desteği hazırdır; ortam değişkenleri AI Studio **Secrets** panelinden yönetilir (`local.properties` kullanılmaz).
- **Doğrudan Gradle / Android Komutları**:
  - Debug APK derleme: `gradle :app:assembleDebug`
  - Birim + Robolectric testleri: `gradle :app:testDebugUnitTest`
- **GitHub'a Aktarım**: AI Studio sağ üst ayar menüsündeki **Push to GitHub** / **Export** seçenekleriyle doğrudan GitHub deponuza gönderilebilir.

---

## Proje Yapısı

```text
.
├── metadata.json                          # AI Studio platform proje metadata dosyası
├── settings.gradle.kts                    # Gradle modül ve depo ayarları
├── gradle/libs.versions.toml              # Sürüm kataloğu (Compose M3, Java-WebSocket, DataStore, Robolectric)
└── app/src/
    ├── main/java/com/example/
    │   ├── MainActivity.kt                # 3 sekmeli Jetpack Compose kabuğu ve tema yönetimi
    │   ├── bridge/
    │   │   ├── AdbPacketizerEngine.kt     # WebSocket <-> ADB TCP 24B header/payload paketleyici
    │   │   ├── AdbPortManager.kt          # Root ADB TCP açma/kapatma ve port yoklama
    │   │   ├── BridgeStateRepository.kt   # Reaktif durum ve paket log deposu
    │   │   └── WebUsbJsGenerator.kt       # Dinamik portlu WebUSB v2 JS & Bookmarklet üreticisi
    │   ├── data/BridgePreferences.kt      # DataStore kalıcı port ve tema tercihleri
    │   ├── service/WebUsbBridgeService.kt # Kalıcı ForegroundService
    │   └── ui/
    │       ├── MainViewModel.kt           # MVVM durum yönetimi
    │       ├── components/                # Ortak minimal UI bileşenleri
    │       ├── screens/                   # Kontrol, Tarayıcı Kodu ve Kayıtlar ekranları
    │       └── theme/                     # Siyah-Kırmızı / Beyaz-Kırmızı M3 tema
    └── test/java/com/example/
        ├── ExampleUnitTest.kt             # Paketleyici, port komutu ve WebUSB v2 JS testleri
        └── ExampleRobolectricTest.kt      # Robolectric kaynak doğrulama testi
```

---

## Teknolojiler

- **Kotlin** & **Jetpack Compose (Material 3)**
- **Java-WebSocket** (`127.0.0.1` loopback WebSocket sunucusu)
- **Kotlin Coroutines & StateFlow**
- **Jetpack DataStore Preferences**
- **JUnit 4 & Robolectric**
