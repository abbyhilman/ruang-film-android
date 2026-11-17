# Ruang Film - Android Native Assessment

Aplikasi Kotlin untuk assessment **Option 1: Movies**. Android saja, dengan Jetpack Compose dan pola MVVM (ViewModel + Repository). Katalog gelap dengan font Inter, banner film, chip genre ungu, grid poster dan tombol putar merah mengikuti referensi Figma yang dipilih. Bahasa antarmuka Indonesia; konten mengikuti bahasa default TMDB.

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
| Genre resmi film | Chip dan pemilih genre mengambil `/genre/movie/list` |
| Discover berdasarkan genre | Grid poster, filter `with_genres`, pagination `/discover/movie` |
| Informasi utama film | Judul, poster, sinopsis, tahun, durasi, genre, rating, pemeran dan IMDb dari `/movie/{id}?append_to_response=credits` |
| Ulasan pengguna | Layar ulasan dari `/movie/{id}/reviews`, teks panjang dapat diperluas |
| Trailer YouTube | Memilih trailer YouTube official terlebih dahulu; dialog pemutar dengan autoplay setelah dipilih, waktu, seek, putar/jeda, tutup dan tautan YouTube |
| Endless scrolling | Daftar film dan ulasan memuat halaman berikutnya mendekati akhir daftar |
| Kasus positif/negatif | Skeleton data/kartu/gambar, kosong, gambar hilang, timeout, koneksi gagal, HTTP error, retry, trailer tidak tersedia |

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
- Poster, banner, foto pemeran dan avatar memakai satu komponen Coil, ruang gambar tetap, skeleton saat memuat dan fallback ketika gambar hilang/gagal.
- Trailer menggunakan YouTube IFrame API dengan autoplay yang diminta setelah aksi pengguna, origin/referrer aplikasi, validasi ID, file/content access dimatikan dan mixed content ditolak. Indikator waktu mengambil posisi/durasi asli; kontrol aktif setelah player siap. Pemutaran dijeda saat aplikasi masuk background dan WebView dihancurkan ketika dialog ditutup.
- Dialog mengikuti layar portrait; video mempertahankan proporsi aslinya. YouTube tetap dapat membatasi autoplay atau embed; kontrol putar dan tautan YouTube menyediakan alternatif. Waktu ditampilkan `--:--` sampai data player tersedia, dan loading player yang tidak merespons dibatasi 20 detik.
- Review dirender sebagai teks, bukan HTML dari API.

## Teknologi

AGP 9.5.1 (built-in Kotlin), Compose compiler 2.4.21, Gradle 9.6.0, Compose BOM 2026.09.00, Lifecycle 2.11.0, Activity Compose 1.13.0, Navigation Compose 2.10.2, Coroutines 1.11.0, Retrofit 3.0.0, OkHttp 5.5.1, Coil 3.6.3. Gradle 9.6.0 dipilih mengikuti versi default yang didokumentasikan untuk AGP 9.5, meskipun ada Gradle yang lebih baru. Versi dipatok; dependency transitif ditentukan Gradle.

## Konfigurasi dan batasan

- `secrets.properties`, `local.properties`, cache, build output dan signing key diabaikan oleh Git. Tidak ada token nyata dalam source yang disiapkan.
- Token baca yang dimasukkan saat build berada di APK dan dapat diekstrak. Jangan gunakan kredensial berprivilege tinggi; untuk kebutuhan produksi dengan rahasia server, gunakan backend yang sesuai.
- Hanya izin internet; tidak ada login pengguna, database, analytics, iklan atau izin penyimpanan. ID film yang disimpan menggunakan SharedPreferences privat aplikasi.
- Tidak ada katalog offline persisten. Coil mengelola cache gambar; koneksi internet dan akses TMDB dibutuhkan untuk data film.
- Ketersediaan review, poster dan trailer mengikuti data TMDB. Beberapa trailer dapat menolak embed; tombol YouTube menyediakan alternatif.
- APK debug untuk evaluasi, bukan release bertanda tangan untuk Play Store.
- Ikon launcher memakai ikon slideshow bawaan Android; identitas aplikasi di dalam UI berupa nama teks.
- Source tersedia di [GitHub](https://github.com/abbyhilman/ruang-film-android). Pengembangan dan pengumpulan perubahan menggunakan branch `main` sesuai pilihan pemilik proyek.

## Pengujian dan dokumentasi

Test menggunakan MockWebServer untuk batas HTTP, coroutine test untuk state/pagination, serta Robolectric + Compose untuk alur UI tanpa perangkat fisik. Semua film, penulis dan ulasan berlabel Fixture hanya terdapat dalam source test, tidak dalam aplikasi.

Verifikasi 2026-11-17: build debug berhasil, 45 test lulus, lint 0 error dan 1 warning informasi Gradle lebih baru, tanda tangan APK debug valid v2. Laporan pengujian dan lint tersedia pada path di bagian Menjalankan. Screenshot native fixture mencakup katalog/detail/ulasan/dialog pada 430 dp, katalog 600 dp, serta layar 320 dp dengan teks 150%. Smoke test HTTP langsung ke TMDB berhasil untuk genre, discover halaman 1/2, pencarian, detail termasuk credits/IMDb, ulasan halaman 1/2, metadata trailer dan CDN poster. Rendering gambar asli dan playback YouTube di perangkat/emulator belum diverifikasi.

## Sumber resmi dan atribusi

- [TMDB API](https://developer.themoviedb.org/reference/intro/getting-started)
- [Discover Movie](https://developer.themoviedb.org/reference/discover-movie)
- [Movie Reviews](https://developer.themoviedb.org/reference/movie-reviews)
- [Movie Videos](https://developer.themoviedb.org/reference/movie-videos)
- [Aturan atribusi TMDB](https://developer.themoviedb.org/docs/faq)
- [Arsitektur Android](https://developer.android.com/topic/architecture/recommendations)
- [YouTube IFrame API](https://developers.google.com/youtube/iframe_api_reference)

This product uses the TMDB API but is not endorsed or certified by TMDB.
Data dan gambar tetap mengikuti hak pemilik serta ketentuan TMDB/YouTube; bukan aset milik aplikasi.

Logo TMDB dalam `app/src/main/assets/tmdb-logo.svg` adalah aset resmi tanpa modifikasi dari [TMDB](https://www.themoviedb.org/assets/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg). Logo ditampilkan pada About dengan proporsi asli untuk mengidentifikasi penyedia data. Library dan Gradle wrapper tetap mengikuti lisensi dan copyright masing-masing.

Font [Inter](https://github.com/google/fonts/tree/main/ofl/inter) disertakan bersama lisensi SIL OFL di `app/src/main/assets/licenses/inter-OFL.txt`. Avatar ilustrasi tamu memakai [DiceBear Notionists](https://www.dicebear.com/styles/notionists/) oleh Zoish (CC0), dibundel agar tidak membutuhkan request profil eksternal.
