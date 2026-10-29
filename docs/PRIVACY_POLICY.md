# Kebijakan Privasi FieldStamp

_Berlaku sejak: [isi tanggal publikasi]_ · Kontak: [isi email dukungan]

FieldStamp adalah aplikasi dokumentasi foto lapangan yang bekerja di perangkat Anda. Kebijakan ini menjelaskan data yang diproses aplikasi.

## Ringkasan
- Tidak ada akun, server, analitik, atau iklan.
- Foto, keterangan, lokasi, proyek, template, dan laporan disimpan **hanya di perangkat Anda**.
- Pengembang tidak menerima data Anda.
- Bila **Nama lokasi otomatis** aktif, koordinat foto dikirim ke layanan geocoding perangkat untuk mendapatkan nama tempat (lihat di bawah).

## Data yang diproses di perangkat
| Data | Tujuan | Disimpan di |
| --- | --- | --- |
| Foto dari kamera atau galeri | Dokumentasi dan laporan | Penyimpanan internal aplikasi |
| Waktu pemotretan (jam perangkat) dan zona waktu | Stempel foto dan pengelompokan laporan | Basis data lokal, EXIF foto berstempel |
| Lokasi (opsional) | Koordinat pada stempel, EXIF, dan laporan | Basis data lokal, EXIF foto berstempel |
| Keterangan, kategori, nama proyek/kegiatan, catatan laporan | Dokumentasi | Basis data lokal |
| Template PNG dan logo instansi yang Anda impor | Bingkai foto dan sampul laporan | Penyimpanan internal aplikasi |

Data aplikasi tidak disertakan dalam pencadangan otomatis Android. Menghapus aplikasi menghapus semua data secara permanen.

## Izin
- **Kamera**: diminta saat Anda membuka kamera.
- **Lokasi** (perkiraan atau akurat): diminta saat pencatatan lokasi aktif, hanya saat aplikasi digunakan. Tidak ada akses lokasi di latar belakang. Pencatatan lokasi dapat dimatikan di Setelan.
- Foto dari galeri dipilih lewat pemilih foto Android. Aplikasi tidak meminta akses ke semua media.

## Nama lokasi otomatis
Bila fitur ini aktif (bawaan: aktif) dan ada internet, aplikasi memakai layanan geocoding bawaan Android (`Geocoder`) untuk mengubah koordinat foto menjadi nama jalan dan wilayah. Pada sebagian besar ponsel Android layanan ini disediakan oleh Google, sehingga **koordinat foto dikirim ke Google** dan diproses sesuai [Kebijakan Privasi Google](https://policies.google.com/privacy). Hanya koordinat yang dikirim; foto dan keterangan tidak. Nama lokasi yang diterima disimpan di perangkat. Fitur ini dapat dimatikan di **Setelan › Nama lokasi otomatis**; nama lokasi tetap dapat diisi manual.

## Berbagi
Data hanya keluar dari aplikasi saat Anda memilih **Bagikan**, **Simpan**, **Cetak**, atau **Ekspor**. Berkas JPEG yang dibagikan dapat memuat waktu, keterangan, dan koordinat dalam data EXIF. Matikan "Simpan GPS di EXIF" bila tidak diinginkan.

## Pembelian
Pembelian FieldStamp Pro diproses oleh Google Play Billing sesuai [Kebijakan Privasi Google](https://policies.google.com/privacy). Aplikasi hanya menyimpan status Pro dan sidik (hash) token pembelian di perangkat.

## Batasan
FieldStamp adalah alat dokumentasi, bukan sistem sertifikasi. Waktu berasal dari jam perangkat yang dapat diubah pengguna, dan data EXIF dapat diubah aplikasi lain.

## Anak-anak
Aplikasi ini ditujukan untuk penggunaan kerja dan tidak ditujukan untuk anak-anak.

## Perubahan
Perubahan kebijakan akan diumumkan melalui halaman ini dan catatan rilis aplikasi.

---

# FieldStamp Privacy Policy (English summary)

FieldStamp has no account, server, analytics or ads. Photos, captions, optional location, projects, templates and reports are stored only on your device and are never sent to the developer. When "automatic place names" is on (default) and the device is online, the photo's coordinates (only) are sent to the device's geocoding service (Google on most Android phones) to obtain a street/area name; this can be turned off in Settings. Camera and foreground location permissions are requested only when used; there is no background location. Data leaves the app only when you choose Share, Save, Print or Export, and shared JPEGs may contain time, caption and GPS in EXIF. Pro purchases are processed by Google Play Billing under Google's privacy policy; the app stores only the Pro status and a hash of the purchase token. Uninstalling the app deletes all data.
