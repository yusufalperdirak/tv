# TV — Android TV uygulaması

Türk kanallarının **resmi YouTube canlı yayınlarını** tek yerde toplayan, kumandayla kullanılan Android TV uygulaması. IPTV listesi kullanmaz.

## Özellikler
- **Ana ekran:** küçük canlı önizleme penceresi, kategoriler (Tümü, Ulusal, Haber, Spor, Belgesel & Müzik, Çocuk, Yurtdışı), her kanalda *Canlı / Yayın yok* etiketi, favoriler.
- **Uygulama içi oynatıcı:** yayın uygulamanın içinde tam ekran oynar. İstersen tek tuşla **YouTube'da aç**.
- **Hızlı kanal listesi:** izlerken OK → soldan liste açılır, yayın arkada devam eder.
- **Otomatik yeniden bağlanma:** yayın donar/kesilirse kendiliğinden yeniden bağlanır. Yayını olmayan kanal, yayın başlayınca kendiliğinden açılır.
- **Türkiye saati:** yerel saatin yanında Türkiye saati (saat farkı varsa).
- **Uyku zamanlayıcısı:** 30 / 60 / 90 dakika.
- **TV açılınca otomatik başlat** ve **son kanalla aç**.
- **Kanal listesi internetten güncellenir** (GitHub ile derlendiyse).

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
`app/src/main/assets/channels.json` — `youtube` alanı kanalın YouTube adresindeki `@kullaniciadi` kısmıdır (youtube.com/**@trthaber**). Listedeki sıra kanal numarasıdır.

## Bilinmesi gerekenler
- Haber kanallarının çoğu YouTube'da 7/24 canlı yayın yapar.
- ATV, Kanal D, Star, Show, NOW, TV8 gibi kanallar yayın hakları nedeniyle çoğu zaman canlı yayın açmaz veya yurtdışına kapatır; uygulama bunu "Yayın yok" olarak gösterir ve resmi uygulamaya yönlendirir. TRT kanalları **tabii** uygulamasında ücretsizdir.
- Bazı kanallar yayınlarının başka uygulamalarda oynatılmasını kapatmış olabilir; o zaman "YouTube'da aç" butonu kullanılır.
