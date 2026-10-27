# HANDOFF - Uhuyy Player

Dokumen ini buat nerusin kerjaan di sesi/akun Claude baru tanpa mulai dari nol.
Pemilik proyek: Choing. Bahasa ngobrol: Indonesia santai (gue/lu). Claude hanya penulis kode; Choing yang rancang, uji, dan build (lewat GitHub Actions).

## Aturan kerja yang sudah disepakati
- Satu ronde per "gas" dari Choing. Jangan lanjut ronde berikutnya tanpa "gas".
- Tiap ronde selesai: kirim ZIP full terbaru + file ini (diperbarui) + prompt lanjutan siap tempel.
- Kalau perubahan ronde bisa dilihat secara visual: kirim juga HTML viewer (pratinjau statis). Kalau cuma bisa dicek di APK, bilang jelas dan jangan kirim HTML.
- Claude TIDAK bisa compile Android di sandbox (Google Maven dan Maven Central diblokir). Verifikasi = baca ulang kode teliti + build GitHub Actions oleh Choing. Kalau build merah, Choing kirim log langkah yang merah.
- Lingkup: app 100% offline. DILARANG izin INTERNET (workflow otomatis gagal kalau ada). Izin lain hanya yang standar dan aman.
- Ukuran APK harus < 100 MB, target < 60 MB.
- Nama file/folder video tetap apa adanya (nggak diterjemahin).

## Status ronde
- [x] Ronde 1 - Fondasi (SELESAI, perlu diuji build oleh Choing)
- [x] Ronde 2 - Player (SELESAI, perlu diuji build + uji di HP oleh Choing)
- [x] Ronde 3 - Home, Pengaturan, Bahasa, foto latar (SELESAI, perlu diuji build + uji di HP oleh Choing)
- [ ] Ronde 4 - Musik saja (layanan latar)
- [ ] Ronde 5 - Format video (decoder FFmpeg)

### Ronde 1 (selesai)
- minSdk 21 (batas Compose + Media3), compileSdk 35, targetSdk 34 (sengaja belum 35 supaya tampilan tepi-ke-tepi Android 15 nggak ngerusak UI; bisa dinaikkan nanti setelah inset ditangani). AGP 8.7.3, Gradle 8.9, Kotlin 2.0.20.
- Release build: R8 + shrink resources, ditandatangani keystore tetap `app/uhuyy.jks` (password uhuyy123, alias uhuyy; cuma buat sideload pribadi). Efek: APK baru tidak bisa menimpa APK debug lama, harus uninstall sekali.
- Workflow: build release; kalau gagal otomatis fallback ke debug + peringatan; cek izin (gagal kalau ada INTERNET) dan ukuran APK.
- Coil dibuang. Thumbnail pakai `ContentResolver.loadThumbnail` (API 29+) / `MediaStore.Video.Thumbnails` (API 21-28) / cadangan MediaMetadataRetriever, cache memori 24 MB, maksimal 3 proses paralel, tunda 70 ms biar item yang cuma lewat saat fling nggak diproses (`Thumbs` di MainActivity.kt).
- Foto latar digambar sebagai layer sendiri (`CompositingStrategy.Offscreen`) supaya nggak digambar ulang tiap scroll.
- Guard versi Android: ImageDecoder (API 28) punya fallback BitmapFactory; `layoutInDisplayCutoutMode` hanya API 28+; semua PiP hanya API 26+ (tombol PiP disembunyikan di bawah itu); ikon adaptive dipindah ke `mipmap-anydpi-v26`, ada ikon legacy `mipmap-nodpi/ic_launcher.png`.

### Ronde 2 (selesai)
Versi app jadi 1.2 (versionCode 3). Dependensi baru: `androidx.compose.animation:animation-core` (BOM). Tidak ada izin baru.
- Bug kontrol muncul lagi setelah double-tap: diperbaiki. `playing` diganti `wantPlay = playWhenReady && state != ENDED` (buffering dianggap main). Tap tunggal dalam 700 ms setelah double-tap/seek diabaikan (`hold.seekAt`).
- Semua gestur sekarang satu pengendali `awaitEachGesture` (tap, double-tap, geser seek/terang/volume, pinch, geser saat zoom). Double-tap seek 5 dtk (kiri 35% / kanan 35% / tengah play-pause).
- Posisi kontrol: prev/pause/next di tengah, jarak = 7% lebar (min 14, maks 56 dp). -5 di kiri dan +5 di kanan, tengah vertikal, 15% lebar dari tepi, dibatasi supaya nggak nabrak grup tengah (portrait). Rumus ada di `BoxWithConstraints` di PlayerActivity.kt dan sama dengan HTML viewer.
- Ikon per mode: rotate (auto / datar / tegak), fit (pas / crop ringan / isi layar / regang). Mode non-bawaan berwarna aksen. Ikon baru di Icons.kt: INFO, ROT_LAND, ROT_PORT, FIT_PAS, FIT_CROP, FIT_STRETCH (FIT lama = isi layar).
- Seekbar custom (`SeekBar`): tap loncat persis; tahan 350 ms lalu geser = skip 5 dtk tiap 26 dp, seek langsung tiap langkah; penanda bulatan aksen (membesar saat mode langkah). Catatan: geser cepat tanpa menahan dihitung sebagai tap di titik lepas (loncat).
- Info video (ikon "i" di bar atas): `buildInfo()`. Sumber: MediaStore (nama, lokasi, ukuran, tanggal, resolusi cadangan) + `player.videoFormat/audioFormat` (kodek, fps, bitrate). Video dari app luar bisa cuma nama + ukuran. Bitrate cadangan = ukuran*8/durasi.
- Pinch zoom: 0.5x sampai 6x, geser satu jari saat zoom, lepas di bawah ~1.04x kembali ke 1x dengan animasi 380 ms. Brightness/volume/seek mati saat zoom. Ketajaman dibatasi resolusi video (nggak bisa meniru upscaling HyperOS). Belum ada tombol reset zoom (cubit balik saja).
- Terang di player: skala -100..100 (`brLevel`). 0..100 = kecerahan window 0.0..1.0; di bawah 0 = window 0.0 + overlay hitam sampai alpha 0.88 (nggak nangkap sentuhan, di bawah HUD dan tombol buka kunci). Awal = kecerahan sistem saat ini.
- Belum teruji compile. Titik yang paling mungkin kena error build: gestur di `PlayerScreen` (awaitEachGesture), `SeekBar`, `buildInfo()` (konstanta MediaStore/MimeTypes), `Animatable.animateTo`.

### Ronde 3 (selesai)
Versi app jadi 1.3 (versionCode 4). Tidak ada dependensi atau izin baru.
- FIX build Ronde 2: `PlayerActivity.kt` kurang `import kotlinx.coroutines.withTimeoutOrNull` (dipakai di SeekBar dan tombol buka kunci). Sudah ditambah. Variabel lokal bernama `t` di PlayerActivity/MainActivity diganti nama supaya nggak bentrok dengan fungsi `t()`.
- `Strings.kt` (BARU): sistem bahasa sendiri. `Lang` (state Compose, disimpan di SharedPreferences "pos" key "lang", nilai "sys" atau kode id/en/zh/es/pt/ja/ko/ru/hi) + `t("key")` dan `tf("key", args)`. Semua terjemahan ada di satu tempat (blok `d(...)`, urutan id,en,zh,es,pt,ja,ko,ru,hi). Ganti bahasa langsung berlaku tanpa restart di Home/Pengaturan. Teks player (mode fit/rotate, HUD, info video, toast) ikut diterjemahkan, tapi layar player nggak di-recompose pas bahasa diganti (bahasa cuma bisa diganti dari Home, jadi aman). Terjemahan dari Claude, bukan profesional; dijelaskan jujur di halaman Bahasa. Nama folder, nama file, dan satuan (fps, kbps) nggak diterjemahkan.
- `Settings.kt` (BARU): `SettingsHost` (halaman 1 Pengaturan, 2 Tema, 3 Bahasa, 4 Tentang, 5 Atur foto). Home tetap ter-compose di bawahnya (alpha 0) supaya posisi scroll nggak hilang. Tombol back sistem balik satu level.
- Home: judul "Uhuyy Player", satu ikon gerigi di pojok kanan atas (gabung Tema + Tentang). Tombol "Tema" lama dan dialognya dihapus.
- Pengaturan: 3 baris berikon: Tema (foto latar + slider), Bahasa (ikut bahasa HP + 9 bahasa, centang yang aktif), Tentang.
- Tentang: logo asli app (`ic_launcher_fg`, diskala 1.44x), nama, versi (dibaca dari PackageManager), tanggal dibuat (konstanta `BUILD_DATE` di Settings.kt, format ikut bahasa), pembuat (`CREATOR` = Choing), kartu "Tentang Pembuat" (Choing rancang/uji/build, kode dibantu AI Claude), kartu offline. Redaksi ada di `Strings.kt` (`honest_text`, `offline_text`).
- Foto latar: pilih foto -> layar Atur posisi (seukuran area Home, geser + cubit 1x-6x, garis sepertiga + bingkai) -> Terapkan / Batal. Yang tersimpan adalah hasil potongan seukuran area Home (maks 1440 px lebar), bukan foto asli. `ThemeState.decodeUri` + `ThemeState.saveCrop` di MainActivity.kt (`importImage` dihapus). Slider gelap/terang tetap ada di halaman Tema, efeknya langsung kelihatan karena foto latar digambar di belakang halaman.
- Ikon baru di Icons.kt: GEAR, GLOBE, CHECK, CHEV.
- Catatan: kalau HP diputar ke landscape, foto latar yang sudah dipotong potret akan di-crop tengah (Home nggak dikunci orientasi). Belum teruji compile. Titik paling mungkin kena error build: `WallpaperAdjust` (drawImage + detectTransformGestures), `Strings.kt` (blok `apply` besar), `SettingsHost`.

## Daftar kerjaan sisa (spesifikasi lengkap dari Choing)

### Ronde 2 - Player (`PlayerActivity.kt`, `Icons.kt`) [SELESAI - spesifikasi asli disimpan buat rujukan]
1. Bug: setelah double-tap seek, kontrol muncul lagi. Penyebab: status buffering dianggap "pause" (`LaunchedEffect(playing)` yang set `showUi = true`). Fix: kontrol auto-muncul hanya kalau pemain benar-benar pause (bukan buffering; pakai `playbackState`/`playWhenReady`), dan tap tunggal tepat setelah seek/double-tap tidak ikut toggle kontrol, supaya double-tap beruntun untuk skip jauh tetap bersih.
2. Icon -5 pindah ke sisi KIRI layar, +5 ke sisi KANAN, tengah vertikal (kira-kira di kotak oranye yang ditandai Choing). Di tengah tinggal prev, pause, next dengan jarak dinamis dan presisi (proporsional lebar layar, rapi di portrait dan landscape).
3. Icon rotate harus beda gambar per mode (auto / datar / tegak). Icon fit beda per mode (pas / crop ringan / isi layar / regang). Mode aktif diberi warna aksen. (Sekarang icon statis dan cuma menampilkan teks sebentar.)
4. Seekbar custom (ganti Slider bawaan):
   - Tap sekali di mana saja: langsung loncat persis ke titik itu.
   - Tekan, tahan, lalu geser: skip pelan, tiap langkah persis 5 detik (seperti tombol seek). Satu langkah per beberapa mm geseran jari, tidak ikut skala panjang durasi.
   - Penanda (thumb) diganti bulatan simple seperti XPlayer; hapus garis + kotak jelek.
5. Tombol info video (icon "i" di bar atas): kartu rapi berisi nama file, folder/lokasi, ukuran, durasi, tanggal, resolusi, kodec video dan audio, fps, bitrate. Detail boleh kosong untuk video dari luar app (keterbatasan Android).
6. Pinch zoom seperti galeri POCO F6: cubit = zoom in, cubit balik = zoom out; lepas di bawah 1x kembali normal dengan animasi pelan; geser satu jari saat zoom untuk pindah area; gesture brightness/volume dimatikan sementara saat zoom. Ketajaman dibatasi resolusi video (upscaling HyperOS tidak bisa ditiru; kasih tahu Choing).
7. Redup/terang layar di dalam player (hanya player): selain brightness window (minimal 0.0), tambah lapisan hitam transparan supaya bisa lebih gelap dari batas sistem sampai benar-benar redup gelap (skala bisa lanjut ke angka minus). Jangan mentok di 3. Cocok untuk ruangan gelap.
8. Kirim HTML viewer (kontrol player, icon per mode, kartu info) di ronde ini.

### Ronde 3 - Home (`MainActivity.kt`, strings) [SELESAI - spesifikasi asli disimpan buat rujukan]
1. Judul/label "Video" diganti "Uhuyy Player". Home dibuat lebih bersih.
2. Pojok kanan atas: satu icon gerigi "Pengaturan" (gabung tombol Tema + Tentang). Isi 3 baris, masing-masing pakai icon: Tema (foto latar + slider), Bahasa, Tentang.
3. Bahasa (9, TANPA Arab): Indonesia, English, 中文, Español, Português, 日本語, 한국어, Русский, हिन्दी + opsi "Ikut bahasa HP". Ganti bahasa langsung berlaku tanpa restart. Terjemahan dari Claude (bukan profesional) - jujur di UI/Tentang.
4. Tentang (UI sebagus dan serapi mungkin): versi, tanggal dibuat, nama Choing. Penjelasan jujur: app ini Choing yang rancang, uji, dan build; kode ditulis dengan bantuan AI (Claude). Redaksi mudah diubah.
5. Foto latar: sebelum diterapkan, tampilkan layar atur posisi (geser + cubit zoom) dengan bingkai seukuran layar HP, lalu tombol Terapkan. Jangan langsung memakai posisi otomatis.
6. Kirim HTML viewer (Pengaturan, Bahasa, Tentang, layar atur foto) di ronde ini.

### Ronde 4 - Musik saja (layanan latar)
- Pindahkan ExoPlayer ke `MediaSessionService` (media3-session) + notifikasi pemutar (prev, play/pause, next) + foreground service tipe mediaPlayback. Activity terhubung ke service.
- Perilaku persis YouTube Premium:
  - Tombol "Musik saja" HANYA satu: di jendela PiP (RemoteAction).
  - Tekan Home / geser navigasi ke atas / geser-tahan ke recent => video disembunyikan, jalur video dimatikan (hemat baterai), audio lanjut main dengan notifikasi. Layar dimatikan tetap main.
  - Home tidak lagi otomatis masuk PiP; PiP hanya lewat tombol PiP di bar atas player.
  - Ada cara balik menampilkan video (ketuk notifikasi / buka app).
- Peringatan baterai: HANYA saat tombol Musik saja di PiP ditekan. Karena PiP tidak bisa menampilkan dialog, app kembali ke layar penuh lalu menampilkan dialog: peringatan baterai lebih boros, checkbox "Jangan tampilkan lagi", tombol Cancel dan OK di kanan bawah. OK => masuk musik saja. Cancel => tetap di video layar penuh. Kalau sudah dicentang => langsung masuk. Pencet Home TIDAK memunculkan dialog.
- Izin yang boleh: FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, POST_NOTIFICATIONS (opsional; diminta sekali saat player pertama dibuka di Android 13+; kalau ditolak, musik tetap jalan hanya tanpa panel notifikasi), WAKE_LOCK bila perlu, + izin baca video yang sudah ada. TIDAK ADA INTERNET.
- HTML viewer: hanya untuk dialog peringatan; sisanya hanya bisa dicek di APK (bilang jelas).

### Ronde 5 - Format video
- Tambah decoder FFmpeg untuk media3 (kandidat: `io.github.anilbeesetti:nextlib-media3ext`, versi harus cocok dengan media3; saat dicek 1.11.1-0.16.0 untuk media3 1.11.1 - VERIFIKASI dulu, sesuaikan versi media3 di build.gradle.kts). Tujuan: audio DTS/AC3/E-AC3 dan codec lain yang tidak didukung bawaan.
- Batasi ABI ke arm64-v8a + armeabi-v7a (HP kentang 32-bit), target APK < 60 MB, laporkan ukuran asli.
- Jujur ke Choing: "semua format" tidak bisa dijamin 100%.
- Tidak ada HTML viewer.
- Pertimbangkan: naikkan targetSdk setelah inset tepi-ke-tepi ditangani, dan uji jalan di Android 17 (API 37; compileSdk 37 butuh AGP lebih baru).

## File penting
- `app/src/main/java/com/uhuyy/player/MainActivity.kt` - Home, daftar video, tema/foto latar, `Thumbs`
- `app/src/main/java/com/uhuyy/player/PlayerActivity.kt` - player, gestur, PiP, kunci
- `app/src/main/java/com/uhuyy/player/Icons.kt` - semua icon digambar sendiri (Canvas)
- `app/src/main/java/com/uhuyy/player/Strings.kt` - bahasa + semua terjemahan
- `app/src/main/java/com/uhuyy/player/Settings.kt` - Pengaturan, Tema, Bahasa, Tentang, layar atur foto
- `.github/workflows/build.yml` - build + cek izin + ukuran
- `BACA-DULU.txt` - panduan repo untuk Choing
