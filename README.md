# TV — Android TV uygulaması

Türk kanallarını tek yerde toplayan, kumandayla kullanılan sade bir Android TV uygulaması.

Bu uygulama Yusuf Alper Dırak tarafından oluşturulmuştur.

**İndir (TV'de Downloader ile):** `github.com/yusufalperdirak/tv/releases/download/latest/TV.apk`

## Özellikler
- **172 kanal**, 11 kategori (Ulusal, Haber, Spor, Eğlence, Film, Belgesel & Kültür, Müzik, Çocuk, Genel, Dini, Yurt Dışı).
- **Ana ekran:** seçili kanal tüm arka planda sessizce oynar; altta kategori satırları ve logolu kanal kartları (kart rengi logodan otomatik).
- **Program rehberi:** şimdi / sonra ve ilerleme çubuğu (listedeki rehber adresinden, 6 saatte bir güncellenir).
- **Favoriler:** ana ekranın en üstünde; sırası değiştirilebilir.
- **Arama:** ekran klavyesiyle veya sesle.
- **İzleme:** altta ince bilgi şeridi; ♥, Yenile, Uyku, Kanal listesi. Kanal değişirken son kare donuk bekler.
- **Kesintiye dayanıklı oynatıcı:** önizlemeden tam ekrana kesintisiz geçiş, canlı yayının birkaç saniye gerisinden oynatma, otomatik yeniden bağlanma.
- **Geri sarma:** izlerken ◀ ▶ ile 10 sn geri/ileri; "Canlıya dön" ile canlı yayına.
- **Uyku zamanlayıcısı** (30/60/90 dk), **TV açılınca otomatik başlat**, **son kanalla aç**.
- **Kanal listesi internetten güncellenir:** `app/src/main/assets/channels.m3u` dosyasını GitHub'da düzenlemek yeterli; TV'deki uygulama bir sonraki açılışta kendini günceller.

## Kumanda
| Tuş | Ana ekran | İzlerken |
|---|---|---|
| ◀ ▶ | Kanallar arasında gez | 10 sn geri / ileri sar |
| ▲ ▼ | Kategoriler arasında gez | Kanal değiştir |
| OK | İzle | Menü (♥, Yenile, Uyku, Kanal listesi, Canlıya dön) |
| OK basılı tut | Favorilere ekle / çıkar · Favoriler satırında: sırala | — |
| 0–9 | — | Kanal numarasına git |
| CH+ / CH− | — | Kanal değiştir |
| Arama tuşu | Sesli arama | — |
| Geri | Çıkış | Ana ekrana dön |

## Derleme
Her `main` güncellemesinde GitHub Actions APK'yı derler ve yukarıdaki indirme linkini günceller (~1 dk). Tüm sürümler aynı anahtarla imzalanır (`signing/tv.jks`), bu yüzden yeni sürüm TV'de eskisinin üzerine kurulur, ayarlar ve favoriler korunur.

Android Studio ile: klasörü aç → **Build → Build APK(s)**.

## TV'ye kurulum
1. **Ayarlar → Sistem → Hakkında → Android TV OS derlemesi** üzerine 7 kez bas (geliştirici modu).
2. Play Store'dan **Downloader** uygulamasını kur, Ayarlar'dan ona "bilinmeyen kaynaklar" izni ver.
3. Downloader'da indirme linkini aç ve **Yükle**'ye bas.
4. İlk açılışta "Diğer uygulamaların üzerinde göster" izni istenirse onayla (otomatik açılış için).

## Kanal listesi
`app/src/main/assets/channels.m3u` — standart M3U. Her kanalın `#EXTINF` satırında `tvg-id` (program rehberi), `tvg-logo` (logo) ve `group-title` (kategori), alt satırda yayın adresi bulunur. Listenin ilk satırındaki `url-tvg` program rehberinin adresidir.

## Bilinmesi gerekenler
- Bazı kanalların yayın adresleri üçüncü taraf sunuculardadır; bu kanallar yavaş açılabilir veya ara ara kesilebilir. Uygulama kendiliğinden yeniden bağlanır, açılamazsa **Yenile** tuşu çıkar.
- Bazı yayınlar bulunduğun ülkeye kapalı olabilir; bunlar ana ekranda soluk görünür.
