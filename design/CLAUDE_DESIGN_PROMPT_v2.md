Ini revisi brief redesign FieldStamp. Brief v1 menghasilkan UI yang rapi tetapi **terlalu datar**: semua layar berupa baris tipis di atas satu warna permukaan, tanpa kedalaman, tanpa foto sebagai pusat perhatian, dan tanpa gerak. Itu salah saya. Brief v1 meminta "tanpa hiasan", dan permintaan itu dijalankan secara harfiah. Brief ini menggantikan bagian "Arah desain" di v1. Batasan, prioritas layar, dan daftar masalah UX di v1 tetap berlaku.

**Jangan pakai design system Nocturne (ungu).** Pakai palet dan font di bawah.

Lampiran:
- `fieldstamp-ui-screens.zip` berisi screenshot app versi lama dan `SCREENS.md`.
- `current-build/` berisi screenshot build terbaru, yaitu hasil stage 1 yang sudah diimplementasikan. Ini titik awalnya. Ada 16 layar, mode gelap, kecuali `17_home_populated_light`.
  - Area preview kamera di `07`/`08` tampak hitam karena HP memblokir screenshot preview kamera. Di HP aslinya, area itu menampilkan gambar langsung dari kamera.
  - Foto contoh berupa dinding polos, dan lokasinya sengaja tidak diizinkan.

## Tujuan app, dan apa yang harus dirasakan pengguna
FieldStamp dipakai guru, pengawas proyek, dan ASN untuk membuktikan bahwa pekerjaan lapangan benar-benar dilakukan. Foto mereka akhirnya masuk ke laporan bulanan yang diserahkan ke atasan.

Ada tiga perasaan yang harus muncul:
1. **Yakin.** "Foto ini tercatat dengan benar: waktu, tempat, dan proyeknya." Setiap stempel, angka GPS, dan status simpan harus terasa pasti dan terkonfirmasi.
2. **Cepat dan puas.** Memotret 30 foto sehari harus terasa ringan, dan setiap jepretan memberi umpan balik yang memuaskan, seperti rana kamera sungguhan.
3. **Bangga dengan hasilnya.** Beranda dan laporan menampilkan **foto-foto mereka sendiri** sebagai isi utama, bukan daftar teks.

Kalau desainnya hanya "rapi tapi sepi", berarti gagal.

## Arah visual: "alat lapangan modern", bukan "formulir"
- **Foto sebagai pusat.** Konten utama app ini adalah foto pengguna.
  - Di Beranda, foto hari ini tampil besar (strip atau grid hero dengan foto terbaru paling besar), bukan baris teks.
  - Status kosong memakai ilustrasi bingkai foto yang bermakna, bukan kotak abu-abu.
- **Kedalaman lewat lapisan tonal**, bukan bayangan tebal:
  - Pakai 4 tingkat permukaan: background → surface → surfaceContainer → raised.
  - Dalam mode gelap, kedalaman ditunjukkan dengan permukaan yang lebih terang, bukan bayangan.
  - Kartu **boleh** dipakai bila mengelompokkan konten yang berkaitan, misalnya kartu konteks hari ini (proyek, kegiatan, template), kartu laporan terakhir, atau kartu proyek dengan cover foto. Yang tetap dilarang adalah kartu di dalam kartu.
- **Warna brand harus benar-benar terasa:**
  - Teal tua `#0E5A6B` (ink) dipakai sebagai warna identitas di header Beranda, tab aktif, dan aksen data.
  - Kuning hi-vis `#FFC21A` dipakai **hanya** untuk aksi tangkap (rana, "Ambil foto").
  - Boleh menambah turunan tonal dari teal untuk permukaan dan container.
- **Tipografi dengan kontras skala yang tegas:**
  - Font: IBM Plex Sans untuk UI, IBM Plex Mono untuk waktu dan koordinat.
  - Tanggal di Beranda dan angka penting (jumlah foto, akurasi ±m) dibuat besar dan berani.
  - Label dan metadata dibuat kecil dan tenang.
- **Ikon:** pakai set yang konsisten (Material Symbols Rounded atau Phosphor), dengan ukuran dan ketebalan seragam.
- **Referensi rasa** (bukan untuk ditiru):
  - kejelasan data di Google Fit/Strava;
  - rasa rana di kamera Pixel;
  - struktur yang tenang di Things 3;
  - kesan "cetak" di aplikasi struk/boarding pass.
- **Tetap hindari:** glassmorphism, gradien ungu, ilustrasi 3D, emoji sebagai ikon, dan statistik dekoratif yang tidak bisa ditindaklanjuti.

## Micro-animasi (wajib, dengan spesifikasi)
Gerak dipakai untuk **memberi konfirmasi dan sebab-akibat**, bukan dekorasi. Setiap animasi harus punya alasan yang bisa disebutkan.

Motion tokens (tulis sebagai token yang bisa dipakai di Compose):
- **Durasi:** `instant` 100ms · `short` 200ms · `medium` 300ms · `long` 450ms.
- **Easing:** Material 3 *emphasized* untuk masuk/keluar dan *standard* untuk perubahan di tempat. Untuk tekan/lepas pakai spring (dampingRatio 0.6–0.8).
- **Aksesibilitas:** semua animasi harus mengikuti setelan sistem "Hapus animasi". Dalam mode itu, transisi diganti fade instan dan tidak ada gerak besar.

Momen yang harus didesain. Tulis untuk setiap momen: pemicu → apa yang bergerak → durasi/easing → haptic.
1. **Rana:**
   - Saat ditekan: scale 0.92 + haptic CONFIRM.
   - Lalu kilat putih 80ms di pratinjau.
   - Thumbnail foto "terbang" ke pojok, menandakan foto sudah ada di antrean tinjau.
2. **Stempel tercetak:** di layar Tinjau, saat pratinjau selesai dirender, blok stempel muncul baris demi baris (waktu → koordinat → tempat), seperti dicap.
3. **Chip GPS:**
   - Saat mencari: titik berdenyut.
   - Saat fix didapat: ikon berubah jadi pin dan angka akurasi ±m berhitung naik ke nilai akhirnya.
   - Warna bergeser netral → hijau atau kuning.
4. **Simpan foto:**
   - Tombol Simpan berubah jadi ✓ (morph), lalu kembali ke kamera.
   - Muncul toast "Foto tersimpan" dari atas.
   - Penghitung "Hari ini" di Beranda bertambah dengan animasi angka bergulir.
5. **Ganti proyek/template dari kamera:** teks chip berganti dengan crossfade + slide kecil, dan bingkai template di pratinjau berganti dengan crossfade.
6. **Daftar dan grid:**
   - Item masuk dengan stagger 20–30ms.
   - Foto baru di grid "Hari ini" muncul dengan scale-in dari 0.9.
   - Hapus foto: item mengecil lalu grid merapat dengan mulus.
7. **Laporan:**
   - Drag-reorder foto: item terangkat (elevasi + scale 1.03) dan item lain bergeser memberi ruang.
   - Saat membuat PDF, progres ditampilkan per halaman (thumbnail halaman menumpuk), lalu selesai dengan konfirmasi ✓.
8. **Navigasi:**
   - Ikon tab berubah dari outline ke filled.
   - Indikator tab aktif bergeser.
   - Transisi antar-layar memakai shared-axis X untuk masuk lebih dalam, dan fade-through antar-tab.
9. **Onboarding dan status kosong:**
   - Ilustrasi punya parallax ringan saat halaman digeser.
   - Contoh stempel di halaman 1 "tercetak" seperti momen 2.
10. **Pull/refresh tidak ada.** Data lokal tidak butuh refresh, jadi jangan ditambahkan.

## Hasil yang saya minta (urutan kerja)
1. **Tiga arah gaya dulu**, masing-masing hanya untuk Beranda (terisi) dan Kamera, dalam mode terang dan gelap. Buat ketiganya benar-benar berbeda dalam kedalaman, kepadatan, dan penggunaan foto. Saya akan memilih salah satu sebelum kamu lanjut.
2. Setelah saya memilih:
   - design tokens: warna (terang/gelap), elevasi/tonal, radius, tipografi, dan **motion**;
   - komponen inti dengan semua statusnya, termasuk status tekan dan transisinya.
3. **Prototipe interaktif yang bisa diklik** untuk alur utama, dengan animasi momen 1–5 benar-benar bergerak:
   Beranda → Kamera → jepret → Tinjau → Simpan → kembali ke Beranda dengan foto baru muncul.
4. Mockup layar sesuai urutan prioritas di brief v1, ukuran 411×914dp.
5. Catatan implementasi per layar:
   - komponen Material 3 / Compose yang dipakai;
   - API animasi yang sesuai (`animateContentSize`, `AnimatedContent`, `updateTransition`, `animateFloatAsState` dengan spring, `SharedTransitionLayout` untuk thumbnail terbang, `Modifier.animateItem()` untuk daftar);
   - haptic per momen.

Jangan menambah fitur baru. Usulan perubahan alur ditulis terpisah.
