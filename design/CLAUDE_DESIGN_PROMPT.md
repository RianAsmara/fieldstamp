Saya melampirkan `fieldstamp-ui-screens.zip`: 76 screenshot dari setiap layar aplikasi Android saya yang sekarang, contoh hasil (foto berstempel dan PDF laporan), serta `SCREENS.md` yang berisi indeks layar, sistem desain saat ini, batasan, dan daftar masalah UX. Baca `SCREENS.md` terlebih dahulu.

## Tentang aplikasi
FieldStamp adalah aplikasi dokumentasi lapangan offline untuk pekerja lapangan, tim proyek, dan ASN di Indonesia. Alurnya:
1. Ambil foto.
2. Foto diberi stempel waktu, nama tempat, GPS, dan proyek/kegiatan, bisa juga di dalam bingkai PNG buatan Canva.
3. Foto dikelompokkan per hari dan proyek.
4. Buat laporan PDF A4 bulanan langsung di HP.

Tanpa akun dan tanpa server. Pengguna bekerja di luar ruangan, sering dengan satu tangan, di bawah terik matahari, memakai HP Android kelas menengah.

## Yang saya minta
Redesign UI aplikasi ini agar terasa seperti alat kerja yang profesional, cepat, dan bisa dipercaya, bukan template Material bawaan. Fitur, alur, dan arsitektur informasinya tetap sama. Yang diperbaiki adalah tampilan, hierarki, dan interaksinya.

### Prioritas layar (kerjakan berurutan)
1. **Beranda** (`01`, `70`): konteks hari ini, tombol "Ambil foto" sebagai aksi utama, template aktif, foto hari ini.
2. **Kamera dan Tinjau hasil** (`06`, `81`, `44`–`47`, `83`): rana besar yang terjangkau ibu jari, chip akurasi GPS, dan form tinjau yang tidak bersaing dengan keyboard.
3. **Editor template** (`18`–`21`, `24`): pratinjau harus tetap terlihat saat pengaturan diubah. Kotak teks diatur dengan **drag dan resize langsung di pratinjau**, bukan 4 slider. Pengaturan lain ditaruh di panel atau sheet bertab.
4. **Pembuat laporan** (`27`–`39`, `77`): tab Foto / Pengaturan / PDF, pengurutan foto per hari, dan status pembuatan PDF.
5. **Galeri dan detail foto** (`07`–`15`, `52`, `71`).
6. **Daftar template, impor, dan dialog** (`16`, `16b`, `23`, `25`, `75`).
7. **Setelan, Pro/paywall, Privasi** (`40`–`43`, `49`–`51`, `26`): pisahkan setelan ke beberapa grup yang jelas.
8. **Proyek** (`02`–`05`, `73`, `74`).

## Arah desain
- **Karakter:** instrumen survei atau buku catatan lapangan. Tegas, padat informasi, tenang, tanpa hiasan.
- **Warna:**
  - Boleh mengusulkan palet baru.
  - Satu warna aksen "hi-vis" khusus untuk aksi tangkap (kamera/rana) dan tidak dipakai di tempat lain.
  - Kontras minimal WCAG AA, dan lebih tinggi untuk teks di atas foto.
- **Tipografi:**
  - Maksimal 2 keluarga font: satu untuk UI dan satu monospace untuk waktu dan koordinat.
  - Sekarang memakai IBM Plex Sans/Mono. Boleh diganti dengan font gratis (OFL) yang bisa dibundel.
- **Mode terang dan gelap** wajib dibuat. Jangan memakai dynamic color.
- **Hindari:** gradien ungu, glassmorphism, ilustrasi 3D, emoji sebagai ikon, kartu bersarang, dan statistik dekoratif.

## Batasan yang wajib dipegang
1. **Kamera dulu.** Kamera harus bisa dibuka dengan satu ketukan dari Beranda.
2. **Terbaca di luar ruangan.** Target sentuh minimal 48dp, dan layout tetap rapi saat skala font sistem 130%.
3. **Jujur.**
   - Waktu dan GPS adalah data perangkat. Jangan pernah menyebutnya "terverifikasi", "anti-manipulasi", atau "tersertifikasi".
   - GPS atau keterangan yang kosong ditampilkan netral, bukan sebagai error.
   - Jangan ada data palsu yang tampil seperti data pengguna.
4. **Setiap status punya desain:** loading, kosong, terisi, tanpa hasil, error, izin ditolak, izin ditolak permanen, offline (billing), dan terkunci Pro.
5. **Aksesibilitas:** setiap tombol ikon punya label, status tidak hanya dibedakan lewat warna, dan bisa dibaca TalkBack.
6. **Teks UI berbahasa Indonesia.** Pertahankan maksud setiap teks (boleh dipersingkat) dan label zona waktu WIB/WITA/WIT. Pemilih tanggal juga harus berbahasa Indonesia.
7. **Android native:**
   - Harus bisa dibangun dengan Jetpack Compose + Material 3.
   - Edge-to-edge dan bottom navigation 4 tab: Beranda · Foto · Laporan · Setelan.
   - Dialog sistem (`22`, `78`, `82`) tidak bisa diubah.
8. **Freemium:** gratis 1 template PNG, sedangkan Pro membuka template tak terbatas, layout dan branding laporan, serta ekspor ZIP. Paywall harus jujur dan tidak memaksa.

## Masalah UX yang harus diselesaikan
Lihat bagian "Known UX issues" di `SCREENS.md`. Masalahnya:
- editor template terlalu panjang;
- label segmented button terpotong;
- judul panjang terpotong di top bar;
- teks "0 foto" muncul dua kali di Beranda kosong;
- pemilih tanggal berbahasa Inggris;
- Setelan terlalu campur aduk;
- tombol Simpan di layar tinjau tertutup keyboard;
- label waktu di grid menimpa stempel di foto.

## Format hasil
1. **Design tokens:** warna (terang dan gelap) dengan nilai hex, skala tipografi (sp), spasi (dp), radius, dan elevasi. Beri nama yang bisa langsung saya pakai di `Theme.kt`.
2. **Komponen inti** beserta statusnya: tombol (primer, sekunder, aksi tangkap), list row, switch row, metadata row, section header, notice (info/peringatan/error), empty state, chip, top bar, bottom nav, dialog, bottom sheet, thumbnail foto (termasuk tile "file hilang"), dan badge Pro.
3. **Mockup layar** sesuai urutan prioritas di atas, ukuran 411×914dp, mode terang. Tambahkan mode gelap untuk Beranda, Kamera, Editor template, dan Laporan.
4. **Catatan implementasi singkat** per layar: komponen Material 3 yang dipakai dan perilaku interaksinya (gesture, animasi, scroll).

Jangan menambah fitur baru. Kalau menurut kamu ada alur yang perlu diubah, tulis sebagai usulan terpisah.
