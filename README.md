# Ruang Film - Android Native Assessment

Aplikasi Kotlin untuk assessment **Option 1: Movies**. Android saja, dengan Jetpack Compose dan pola MVVM (ViewModel + Repository). Katalog gelap memakai font Inter dan token Figma; alignment, banner dan pemilih genre diperbaiki berdasarkan screenshot perangkat. Bahasa antarmuka Indonesia; konten mengikuti bahasa default TMDB.

## Download APK untuk pengujian

Untuk mencoba aplikasi, unduh [APK Ruang Film melalui Google Drive](https://drive.google.com/file/d/1lomYqAPuc9BOyYS9W8J1K8TRunP2hovp/view?usp=sharing).

## Menjalankan

1. Buka folder ini melalui Android Studio yang mendukung AGP 9.5.
2. Gunakan JDK 17 atau 21. Pasang Android SDK Platform 37.0 dan Build Tools 36.0.0 melalui SDK Manager.
3. Pastikan lokasi SDK tersedia melalui `ANDROID_HOME` atau `local.properties`, contoh `sdk.dir=C\:/Android/Sdk`.
4. Salin `secrets.properties.example` menjadi `secrets.properties`.
5. Isi `TMDB_READ_ACCESS_TOKEN` dengan **API Read Access Token** dari pengaturan API akun TMDB, bukan API key v3. Alternatif: environment variable dengan nama yang sama.
6. Jalankan konfigurasi `app` pada emulator/perangkat Android 8.0 (API 26) atau lebih baru.

Token kosong tetap memungkinkan build dan test. Aplikasi menampilkan pesan konfigurasi ketika mencoba memuat data; aplikasi tidak mengganti data asli dengan data demo. Perubahan token memerlukan rebuild.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.
Laporan pengujian: `app/build/reports/tests/testDebugUnitTest/index.html`.
Laporan lint: `app/build/reports/lint-results-debug.html`.

## Cakupan assessment

| User story | Implementasi |
| --- | --- |
| Genre resmi film | Chip dan dialog grid genre mengambil `/genre/movie/list`; dua kolom pada ponsel normal, adaptif untuk layar lebar/teks besar |
| Discover berdasarkan genre | Grid poster, filter `with_genres`, pagination `/discover/movie` |
| Informasi utama film | Judul, poster, sinopsis, tahun, durasi, genre, rating, pemeran dan IMDb dari `/movie/{id}?append_to_response=credits` |
| Ulasan pengguna | Layar ulasan dari `/movie/{id}/reviews`, teks panjang dapat diperluas |
| Trailer YouTube | Memilih trailer YouTube official terlebih dahulu; dialog pemutar dengan autoplay setelah dipilih, waktu, seek, putar/jeda, tutup dan tautan YouTube |
| Endless scrolling | Daftar film dan ulasan memuat halaman berikutnya mendekati akhir daftar |
| Kasus positif/negatif | Skeleton shimmer data/kartu/gambar/player, kosong, gambar hilang, timeout, koneksi gagal, HTTP error, retry, trailer tidak tersedia |

Alur: **Katalog / Pencarian -> Detail -> Ulasan / Dialog trailer**. Pencarian menggunakan `/search/movie` dengan debounce 350 ms, pembatalan request lama dan pagination. Ikon hati menyimpan ID film secara lokal; tombol share membuka lembar berbagi Android. Avatar tamu membuka informasi aplikasi dan atribusi.

Tombol kembali dan back sistem mengikuti back stack. Halaman detail dan daftar menggunakan state ViewModel; posisi scroll disimpan oleh state Compose selama navigasi/rekreasi activity. Setelah process death, ID rute dipulihkan dan data diambil ulang.

## Struktur dan keputusan

```text
app/src/main/java/com/assessment/movies/
  MainActivity.kt           entry point dan navigasi
  data/                    kontrak model, Retrofit DTO, mapping dan repository TMDB
  ui/                      state, ViewModel, layar Compose dan token tema
  ui/components/           gambar, kartu, chip, rating, skeleton, ulasan dan dialog trailer global
app/src/test/              test pagination, repository HTTP, ViewModel dan navigasi UI
```

- Satu modul `app` agar setup assessment mudah ditinjau. Tidak ada lapisan use-case kosong atau container DI yang tidak diperlukan.
- Repository memisahkan transport dan mapping dari UI. Factory ViewModel memasukkan repository secara eksplisit dan memungkinkan fixture terisolasi pada test.
- Pagination khusus yang kecil: satu request aktif per daftar, deduplikasi berdasarkan ID, retry halaman yang sama, mempertahankan hasil sebelumnya, berhenti pada halaman terakhir/hasil kosong/batas halaman 500 TMDB.
- Detail dan trailer dimuat terpisah: kegagalan trailer tidak menghilangkan informasi film.
- Tidak ada retry otomatis tanpa batas. Pengguna memicu retry untuk error; scrolling tidak mengulang request yang gagal secara terus-menerus.
- Header, pencarian, banner pertama, genre dan poster memakai margin konten 16 dp yang sama. Tombol play banner berbentuk lingkaran 40 dp dalam target sentuh 48 dp.
- Kategori Home memakai LazyRow berlabel satu baris sesuai panjang isi, gap 8 dp dan target minimum 48 dp. Genre yang tersedia tetap tampil ketika film filter berikutnya memuat; state scroll dipertahankan di luar cabang loading. Tidak ada fixed width atau perubahan clipping. Dialog grid genre tetap mendukung label dua baris.
- Saat mask siap, transisi poster dan banner berlangsung 640 ms. Mask berubah pada 96 ms awal dan akhir, sedangkan posisi ditahan selama 144 ms di tiap ujung untuk memberi jarak antara perubahan latar dan gerak subjek. Gambar pilihan tersedia sebagai preview sebelum API detail selesai. Posisi katalog tetap dipertahankan saat Back dan animasi mengikuti pengaturan animator sistem.
- Mask subjek dibuat dari gambar setiap film menggunakan ML Kit di perangkat, tanpa daftar judul/ID khusus. Mask mempertahankan foreground yang cukup yakin dengan tepi lembut. Latar dihapus sepanjang gerak utama, subjek mengikuti pusat layar, dan judul TMDB ikut bergerak saat buka maupun Back. Gradient/kontrol hero dan isi layar ikut dipisahkan waktunya agar tidak melayang di belakang karakter. Mask yang terlambat baru diterapkan setelah route idle sehingga dapat dipakai untuk Back tanpa mengubah bentuk di tengah gerakan.
- Mask disiapkan di latar belakang saat Coil memuat poster atau banner. Input memakai gambar yang lebih besar dengan sisi terpanjang dibatasi 1024 px. Pemrosesan berjalan satu per satu, memprioritaskan gambar yang dipilih jika masih dalam antrean, dan menggunakan cache hingga 12 mask di memori serta 48 mask di penyimpanan lokal. Jika mask belum siap, poster utuh bergerak sekitar 300 ms dengan crossfade layar. Jika pasangan gambar tidak tersedia, crossfade tetap bisa berjalan.
- Banner menampilkan bahasa asli dari metadata TMDB. Durasi diambil hanya untuk banner yang sedang terlihat melalui endpoint detail yang sudah ada. Cache memori LRU maksimal 40 detail dipakai kembali ketika film dibuka; kegagalan metadata opsional tidak menghilangkan katalog.
- Poster, banner, foto pemeran dan avatar memakai satu komponen Coil, ruang gambar tetap, shimmer saat memuat dan fallback ketika gambar hilang/gagal. Shimmer juga menandai pencarian yang sedang memuat; kolom tetap dapat diedit. Animasi berhenti ketika tidak memuat/layar tidak aktif dan memakai placeholder statis bila animasi sistem dimatikan.
- Trailer menggunakan YouTube IFrame API dengan autoplay yang diminta setelah aksi pengguna, origin/referrer aplikasi, validasi ID, file/content access dimatikan dan mixed content ditolak. Waktu, seek dan putar/jeda hanya memakai kontrol bawaan YouTube di dalam video; tidak ada slider atau tombol playback tambahan di luar video. Pemutaran dijeda saat aplikasi masuk background dan WebView dihancurkan ketika dialog ditutup.
- Dialog mengikuti layar portrait; video mempertahankan proporsi aslinya. YouTube tetap dapat membatasi autoplay atau embed; kontrol pemutar YouTube dan tautan eksternal menyediakan alternatif. Loading player yang tidak merespons dibatasi 20 detik, sedangkan tutup dan retry tetap tersedia.
- Tombol fullscreen bawaan YouTube diaktifkan melalui `fs=1` dan izin iframe fullscreen. WebChromeClient menampilkan custom view dalam window layar penuh tanpa melepas/membuat ulang WebView inline. Back atau kontrol keluar fullscreen kembali ke dialog; penutupan player dan renderer yang mati juga melepaskan window tersebut. Orientasi Activity tidak dipaksa dan sistem bar hanya disembunyikan pada window fullscreen.
- WebView dibuat oleh factory AndroidView dan memuat HTML setelah attach/layout. Area media tepat 16:9, tanpa clipping membulat di sekitar media. Renderer yang berhenti ditangani dengan pelepasan view dan pilihan muat ulang; membuka YouTube menjeda pemutar dalam aplikasi. Build debug mencatat hardware acceleration, ukuran dan versi provider pada tag Logcat `TrailerPlayer`, tanpa URL atau token.
- Review dirender sebagai teks, bukan HTML dari API.

## Teknologi

AGP 9.5.1 (built-in Kotlin), Compose compiler 2.4.21, Gradle 9.6.0, Compose BOM 2026.09.00, Lifecycle 2.11.0, Activity Compose 1.13.0, Core Splashscreen 1.2.0, Navigation Compose 2.10.2, Coroutines 1.11.0, Retrofit 3.0.0, OkHttp 5.5.1, Coil 3.6.3. Gradle 9.6.0 dipilih mengikuti versi default yang didokumentasikan untuk AGP 9.5, meskipun ada Gradle yang lebih baru. Versi dipatok; dependency transitif ditentukan Gradle.

Mask memakai `play-services-mlkit-subject-segmentation:16.0.0-beta1`. Model diunduh melalui Google Play services sebelum dapat digunakan; proses deteksi menggunakan bitmap lokal. SDK masih beta. Pemeriksaan/permintaan model dibatasi 30 detik dan kegagalannya tidak memblokir katalog atau navigasi. APK yang baru diinstal dapat memakai transisi gambar utuh pada pembukaan pertama.

## Konfigurasi dan batasan

- `secrets.properties`, `local.properties`, cache, build output dan signing key diabaikan oleh Git. Tidak ada token nyata dalam source yang disiapkan.
- Token baca yang dimasukkan saat build berada di APK dan dapat diekstrak. Jangan gunakan kredensial berprivilege tinggi; untuk kebutuhan produksi dengan rahasia server, gunakan backend yang sesuai.
- Aplikasi meminta izin internet; tidak ada login pengguna, database katalog, iklan atau izin penyimpanan. ID film yang disimpan menggunakan SharedPreferences privat aplikasi.
- Tidak ada katalog offline persisten. Coil mengelola cache gambar; koneksi internet dan akses TMDB dibutuhkan untuk data film.
- Ketersediaan review, poster dan trailer mengikuti data TMDB. Beberapa trailer dapat menolak embed; tombol YouTube menyediakan alternatif.
- APK debug untuk evaluasi, bukan release bertanda tangan untuk Play Store.
- Logo A, Ruang R, dipilih pemilik proyek. Master SVG dan palette ada di `brand/`; launcher memakai adaptive foreground/background, monochrome pada launcher pendukung Android 13+, serta PNG lima density.
- AndroidX mengelola splash sistem dan post-theme. Compose menampilkan logo R dan membuka Home dengan zoom 900 ms dari pusat layar aplikasi. Reveal dimulai setelah frame awal Compose, termasuk pada pembukaan pertama seusai pemasangan atau update. Pembukaan pertama diperiksa dari waktu pembaruan paket Android, sehingga state Activity yang dipulihkan setelah update masih bisa menampilkan reveal. Animasi mengikuti pengaturan animator sistem.
- Source tersedia di [GitHub](https://github.com/abbyhilman/ruang-film-android). Pengembangan dan pengumpulan perubahan menggunakan branch `main` sesuai pilihan pemilik proyek.

## Pengujian dan dokumentasi

Test menggunakan MockWebServer untuk batas HTTP, coroutine test untuk state/pagination, serta Robolectric + Compose untuk alur UI tanpa perangkat fisik. Semua film, penulis dan ulasan berlabel Fixture hanya terdapat dalam source test, tidak dalam aplikasi.

Verifikasi 2026-11-20: build debug berhasil, 85 test lulus tanpa failure/error/skipped, lint 0 error/3 warning saran versi Gradle/Lifecycle, tanda tangan APK v2 valid. Sebelas test artwork memeriksa frame buka/Back, pemisahan latar dengan matte uji, gerak judul, pusat viewport, preview saat detail tertunda/error/retry, cache, asal banner/poster, reduced motion, ketukan berulang, share preview dan posisi scroll. Lima test mask memeriksa alpha lembut, input/deteksi ambigu, identitas gambar lintas film dan pusat subjek setelah crop. Uji dialog memastikan slider/waktu/tombol playback tambahan tidak muncul, sementara tutup dan tautan YouTube tetap bekerja. Ini menguji komposisi matte dan kontrak; model ML Kit sebenarnya belum dijalankan di perangkat oleh agen.

Screenshot native fixture sebelumnya mencakup katalog/detail/ulasan/dialog pada 430 dp, katalog 600 dp, serta layar 320 dp dengan teks 150%. Lima test kategori memeriksa swipe, item terakhir, label, margin/gap/target sentuh, filter saat loading dan kembali dari detail pada 320/360/430/800 dp, density mdpi/xhdpi/xxhdpi dan font hingga 200%. Suite juga mencakup repository, pagination, cache, lifecycle pemutar, splash cutout, reduced motion dan pemulihan interaksi Home. Adaptive icon diperiksa pada API 26 dan API 35; ini bukan pengujian seluruh alur aplikasi pada Android 8. Smoke test HTTP TMDB sebelumnya berhasil untuk endpoint assessment; tidak diulang untuk perubahan transisi.

Pengujian terbaru juga memeriksa sisa warna latar pada beberapa frame buka/Back, fallback tanpa mask, serta host fullscreen masuk/Back/keluar native/re-entry/duplicate/close/renderer death. Pixel memakai pola Canvas terkontrol; host fullscreen diuji melalui callback WebChromeClient. Ini bukan pengujian video YouTube fullscreen pada ponsel. Mask diberi inset satu piksel input agar feather resampling tetap berada di dalam subjek.

Pengguna telah mengonfirmasi trailer berhasil diputar dengan gambar dan audio pada ponselnya setelah perbaikan sebelumnya. Konfirmasi tersebut berasal dari pengujian pengguna; fullscreen dan kelancaran terbaru belum dikonfirmasi pada perangkat.

Kualitas segmentasi poster asli, unduhan model pada instalasi pertama, animasi di hardware, handoff splash sistem, cold/warm/rotasi pada launcher/vendor nyata dan frame pacing belum diperiksa langsung oleh agen pada perangkat. ADB tidak mendeteksi perangkat/emulator pada 20 November 2026. Hasil mask mengikuti kemampuan deteksi gambar; tidak ada jaminan setiap poster menghasilkan cutout yang tepat. Test Compose membuktikan frame perantara shared element, matte uji dan state, tanpa menyatakan angka fps. Pada deep link/rekreasi atau elemen di luar viewport, navigasi tetap memakai transisi layar biasa ketika pasangan shared element tidak tersedia.

## Sumber resmi dan atribusi

- [TMDB API](https://developer.themoviedb.org/reference/intro/getting-started)
- [Discover Movie](https://developer.themoviedb.org/reference/discover-movie)
- [Movie Reviews](https://developer.themoviedb.org/reference/movie-reviews)
- [Movie Videos](https://developer.themoviedb.org/reference/movie-videos)
- [Aturan atribusi TMDB](https://developer.themoviedb.org/docs/faq)
- [Arsitektur Android](https://developer.android.com/topic/architecture/recommendations)
- [YouTube IFrame API](https://developers.google.com/youtube/iframe_api_reference)
- [ML Kit Subject Segmentation Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android)
- [Model Google Play services](https://developers.google.com/android/guides/module-install-apis)

This product uses the TMDB API but is not endorsed or certified by TMDB.
Data dan gambar tetap mengikuti hak pemilik serta ketentuan TMDB/YouTube; bukan aset milik aplikasi.

Logo TMDB dalam `app/src/main/assets/tmdb-logo.svg` adalah aset resmi tanpa modifikasi dari [TMDB](https://www.themoviedb.org/assets/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg). Logo ditampilkan pada About dengan proporsi asli untuk mengidentifikasi penyedia data. Library dan Gradle wrapper tetap mengikuti lisensi dan copyright masing-masing.

Font [Inter](https://github.com/google/fonts/tree/main/ofl/inter) disertakan bersama lisensi SIL OFL di `app/src/main/assets/licenses/inter-OFL.txt`. Avatar ilustrasi tamu memakai [DiceBear Notionists](https://www.dicebear.com/styles/notionists/) oleh Zoish (CC0), dibundel agar tidak membutuhkan request profil eksternal.
