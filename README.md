# TV — Android TV uygulaması

Türk kanallarının **resmi YouTube canlı yayınlarını** tek yerde toplayan, kumandayla kullanılan Android TV uygulaması. IPTV listesi kullanmaz.

## Özellikler
- **172 kanal**, kategorilere ayrılmış (Ulusal, Haber, Spor, Eğlence, Film, Belgesel & Kültür, Müzik, Çocuk, Genel, Dini, Yurt Dışı), kanal logolarıyla.
- **Ana ekran:** küçük canlı önizleme penceresi, her kanalda *Canlı / Kapalı* etiketi, favoriler.
- **Yenile tuşu:** yayın açılmazsa veya takılırsa tek tuşla yeniden açar. Ayrıca kendiliğinden yeniden bağlanır.
- **Hızlı kanal listesi:** izlerken OK → soldan liste açılır, yayın arkada devam eder.
- **Türkiye saati**, **uyku zamanlayıcısı** (30/60/90 dk), **TV açılınca otomatik başlat**, **son kanalla aç**.
- **Kanal listesi internetten güncellenir:** `app/src/main/assets/channels.m3u` dosyasını GitHub'da düzenlemek yeterli.
- Açılışta kısa bir yükleme ekranı.

Bu uygulama Yusuf Alper Dırak tarafından oluşturulmuştur.

## Kumanda
| Tuş | Ana ekran | İzlerken |
|---|---|---|
| Yön tuşları | Kanallar arasında gez (önizleme değişir) | ▲▼ kanal değiştir · ◀▶ alt menü |
| OK | Tam ekran izle | Hızlı kanal listesi |
| 0–9 | — | Kanal numarasına git |
| CH+/CH− | — | Kanal değiştir |
| Geri | Çıkış | Ana ekrana dön |

## APK'yı derleme

### Yol A — GitHub (önerilen, bilgisayar gerekmez)
1. github.com'da ücretsiz hesap aç → **New repository** → adı örn. `tv`, **Public** seç → oluştur.
2. **Add file → Upload files** ile bu klasördeki her şeyi yükle (`.github` klasörü dahil; klasörleri sürükleyip bırakabilirsin).
3. **Actions** sekmesinde "APK derle" işi otomatik çalışır (~5 dk).
4. Bitince işin sayfasındaki **Artifacts → TurkTV-apk** dosyasını indir, içindeki APK'yı TV'ye kur.

Bu yolla derlenen uygulama, kanal listesini her açılışta **o depodaki** `app/src/main/assets/channels.json` dosyasından günceller. Yani bir kanal adresini düzeltmek için sadece GitHub'da o dosyayı düzenlemen yeterli; APK'yı yeniden kurmana gerek yok.

### Yol B — Android Studio (bilgisayar)
1. Android Studio'yu kur, bu klasörü **Open** ile aç.
2. (İsteğe bağlı, uzaktan güncelleme için) `gradle.properties` dosyasına `channelsRepo=kullaniciadi/depoadi` satırını ekle.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## TV'ye kurulum
1. TV'de **Ayarlar → Sistem/Cihaz tercihleri → Hakkında → Android TV OS derlemesi** üzerine 7 kez bas (geliştirici modu).
2. Play Store'dan **Downloader** veya **Send Files to TV** uygulamasını kur, APK'yı TV'ye gönder ve aç.
3. "Bilinmeyen kaynaklar" izni sorarsa o uygulamaya izin ver.

## Otomatik açılış hakkında
- İlk açılışta "Diğer uygulamaların üzerinde göster" izni istenir; bir kez onayla. Ekran açılmazsa: **Ayarlar → Uygulamalar → Özel uygulama erişimi → Diğer uygulamaların üzerinde göster → TV**.
- TV bekleme modundan açıldığında ve tamamen kapanıp açıldığında uygulama kendiliğinden başlar.
- Bazı üreticiler (özellikle yeni Google TV cihazları) bu tür otomatik başlatmayı kısıtlayabilir.

## Kanal listesi
`app/src/main/assets/channels.m3u` — standart M3U biçimi. Her kanal için `#EXTINF` satırında `group-title` (kategori) ve `tvg-logo` (logo), alt satırda yayın adresi bulunur. Listedeki sıra, kategori sırasıyla birlikte kanal numarasını belirler.

