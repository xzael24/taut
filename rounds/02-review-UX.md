# Round 2: Review — UX Designer

---

## Project: **TAUT** — Platform Daur Ulang Digital

**Reviewer:** UX Designer (User Experience Lead)
**Date:** 23 Juni 2026
**Review Type:** Critical UX Evaluation of CIO Proposal

---

## Executive Summary

TAUT menyelesaikan masalah nyata yang nyata — ekosistem daur ulang Indonesia membutuhkan infrastruktur data digital. Namun, proposal ini sangat berfokus pada *apa* yang akan dibangun, dan hampir tidak membahas *bagaimana* target pengguna sebenarnya akan menggunakannya. Dari sudut pandang UX, ada kesenjangan kritis antara asumsi proposal dan realitas pengguna yang diharapkan. Review ini mengidentifikasi celah-celah tersebut dan menyarankan perubahan yang HARUS dilakukan sebelum masuk ke fase desain/enginering.

---

## 1. Profil Pengguna: Desain Untuk SIAPA?

Proposal menyebut pengguna utama adalah **operator bank sampah, usia 40-55 tahun, literasi digital rendah**. Ini bukan sekadar demografi — ini menentukan SEMUA keputusan desain.

### Realitas Lapangan yang Harus Didesain:

| Aspek | Realita | Implikasi Desain |
|-------|---------|-----------------|
| **Usia 40-55** | Visi menurun, butuh tombol/font lebih besar; tidak terbiasa gestur pinch-zoom | Minimum touch target 48x48dp, font 18sp+, high contrast |
| **Literasi digital rendah** | Tidak paham istilah "dashboard", "sync", "OTP" | Zero jargon dalam UI — bahasa sehari-hari Indonesia |
| **HP Rp500 ribuan** | RAM 1-2GB, layar 5-5.5", GPU lemah, kapasitas storage 8-16GB | Aplikasi harus <15MB (sudah disebut), TIDAK ada animasi berat, offline-first bukan hanya "nice to have" |
| **Literasi baca-tulis** | Bisa baca bahasa Indonesia dasar, tapi tidak nyaman membaca paragraf panjang | **90% komunikasi via ikon + warna + angka**, bukan teks |
| **Kepercayaan** | Lebih percaya pada orang (kader,RT) daripada teknologi | Butuh "champion" pengguna internal — bukan sekadar onboarding video |
| **Budaya** | Terbiasa buku catatan fisik, uang tunai, interaksi tatap muka | Setiap langkah digital harus punya analogi fisik yang jelas |

### Kritik Proposal:
Proposal menyebutkan *"UI icon-based minimalis; onboarding via komunitas/kader"* di tabel risiko — tapi ini hanya satu baris mitigasi, bukan pendesainan serius. **Pendekatan UX untuk pengguna ini harus jadi KERANGKA KERJA utama, bukan catatan kaki.**

---

## 2. Offline-First di HP Murah — Evaluasi Mendalam

Proposal menulis: *"offline-first dengan periodic sync; anti-ghost data saat offline."* Klaim ini bagus di permukaan, tapi perlu diuji.

### Apa yang Harus Diuji:

**Apa yang "offline-first" HARUS artikan untuk pengguna ini:**
1. **Tidak ada layar loading/spinner** yang ambigu — pengguna harus tahu persis: "Data tersimpan. Akan dikirim nanti."
2. **Status sinkronisasi harus selalu terlihat** — tapi bukan di bagian atas layar (mereka tidak akan melihatnya), melainkan **pada setiap transaksi**: ✅ sudah dikirim vs 🔄 menunggu kirim
3. **Tidak ada data yang hilang** — proposal menyebut "anti-ghost data" tapi tidak menjelaskan mekanismenya. Ini KRITIS:
   - Apa yang terjadi jika HP mati di tengah pencatatan?
   - Apa yang terjadi jika aplikasi crash?
   - Bagaimana operator tahu bahwa data mereka aman?

**Batasan teknis yang harus diperhitungkan:**
- HP Rp500 ribu (Redmi A2, Samsung A05, dll.) punya **RAM 2-3GB**. Aplikasi harus berjalan tanpa freeze.
- **Layar kecil (5-5.5")** — layout harus dirancang untuk portrait-only, NO horizontal scrolling.
- **Penyimpanan terbatas** — data lokal harus punya mekanisme cleanup otomatis. Jangan biarkan 6 bulan data menumpuk di SQLite hingga HP error.
- **Baterai** — banyak pengguna hanya charge semalam. Aplikasi tidak boleh boros baterai di background.

### Apa yang TIDAK Disebutkan dalam Proposal:
- **Offline camera untuk QR scan** — apakah ini diperlukan? Jika nasabah bawa QR fisik, operator perlu scan offline.
- **Mode pesawat testing** — bagaimana tim QA akan testing skenario offline secara realistis?
- **Conflict resolution** — jika 2 operator di lokasi berbeda sync data yang bertabrakan (stok yang sama), bagaimana?

### Rekomendasi:
Buat **"Offline UX Contract"** — dokumen yang mendefinisikan secara eksplisit apa yang terjadi dalam setiap skenario offline. Ini bukan optional; ini foundational untuk TAUT.

---

## 3. Icon-Based Navigation — Desain untuk Non-Reader

### Evaluasi Kritis:

Proposal mengatakan "icon-based minimalis" tapi tidak mendesain SISTEM ikon. Ini berbahaya karena:

**Masalah dengan ikon universal:**
- Tidak semua ikon universal. 🏠 = Home diakui luas, tapi 📊 = Dashboard? ❓
- Operator bank sampah mungkin tidak tahu ikon standar tech industry
- Beberapa kategori sampah sulit dibedakan secara visual (plastik bening vs plastik putih vs styrofoam)

**Sistem Ikon yang Diperlukan:**

```
Layar Utama (maksimal 3-4 ikon besar):
┌──────────────────────────────────────┐
│                                      │
│    [⚖️ Timbang]      [💰 Harga]     │
│                                      │
│    [📋 Riwayat]      [⚙️ Atur]      │
│                                      │
└──────────────────────────────────────┘

Setiap ikon: 80x80dp minimum
Setiap ikon: DIAcompany teks BESAR di bawahnya
Warna kode: Hijau = timbang, Biru = harga, Kuning = riwayat
```

**Prinsip Desain Ikon untuk TAUT:**

1. **Ikon + Teks selalu berpasangan** — jangan pernah ikon tanpa label
2. **Warna sebagai redundant coding** — hijau untuk aksi utama (timbang), biru untuk informasi (harga), abu untuk sekunder
3. **Ikon kategori sampah harus foto real**, bukan ilustrasi vektor — pengguna tahu "botol Aqua" bukan "botol generik"
4. **Ukuran minimum 80x80dp** untuk navigasi utama, 64x64dp untuk item list
5. **Satu halaman = satu aksi** — tidak ada multi-step flow dalam satu layar

### Rekomendasi:
Lakukan **card sorting study** dengan 10-15 operator bank sampah untuk menentukan kategori ikon. JANGAN menebak — pengguna akan punya mental model yang berbeda dari tim tech.

---

## 4. QR Receipt Flow — Apakah Intuitif?

### Current Flow dalam Proposal:
```
Rumah tangga setor sampah → Operator timbang + catat di app → QR code terbuat → Rumah tangga terima QR sebagai bukti
```

### Evaluasi per Langkah:

**Langkah 1: Operator input data**
- ✅ "Timbang → pilih kategori → input berat" sudah cukup logis
- ⚠️ **Tapi:** Bagaimana dengan kategori yang tidak ada di daftar? Apakah ada "Lainnya"? Apakah operator bisa menambah kategori?
- ⚠️ **Tapi:** Apakah operator harus input berat manual dari timbangan digital, atau terintegrasi dengan Bluetooth scale? Jika manual, potensi human error TINGGI.
- ⚠️ **Tapi:** Bagaimana dengan sampah campur? Bank sampah sering menerima "karung campur" — ini harus ditangani.

**Langkah 2: QR code terbuat**
- ❌ **Masalah: Untuk siapa QR ini?**
  - Apakah QR untuk operator? (Operator sudah punya catatan di app — untuk apa QR?)
  - Apakah QR untuk rumah tangga? (Rumah tangga harus install app juga untuk scan?)
  - Apakah QR ini dicetak fisik? (Makanan printer/batere)
  - **Proposal tidak menjelaskan flow QR ini dengan cukup detail.**

**Langkah 3: Nasabah "terima QR"**
- ❌ **Ini adalah titik kegagalan terbesar:**
  - Jika nasabah tidak punya app → mereka tidak bisa scan QR
  - Jika QR hanya ditampilkan di layar operator → nasabah harus foto layar → untuk apa?
  - Jika QR dicetak → butuh thermal printer (~Rp300-500 ribu) — ini biaya tambahan yang tidak disebut
  - **Jika nasabah tidak punya HP atau tidak bisa scan → seluruh flow QR ini useless**

### QR Receipt Flow Alternatif yang Lebih Realistis:

**Opsi A: SMS Receipt (Recommended untuk MVP)**
- Setelah operator input transaksi → kirim SMS otomatis ke nomor telepon nasabah
- SMS berisi: "Setor 3kg plastik botol. Nilai: Rp 6.000. No: T-20260623-001. Saldo: Rp 45.000"
- **Tidak perlu app di sisi nasabah. Tidak perlu internet. Tidak perlu scan.**
- Nomor telepon Indonesia penetrate 95%+ — bahkan HP Nokia bisa terima SMS

**Opsi B: Slip Fisik Sederhana**
- Operator cetak atau tulis slip sederhana dengan nomor transaksi
- Nasabah simpan slip → kapanpun ingin cek saldo atau klaim poin → kasih nomor ke operator
- **Analogi yang sudah dikenal: struk ATM / bon belanja**

**Opsi C: QR tetap ada, tapi sebagai.opsi premium**
- Untuk nasabah yang punya HP → QR di layar operator, nasabah foto/scan
- Untuk nasabah yang tidak punya HP → SMS atau slip fisik
- **JANGAN jadikan QR sebagai satu-satunya mekanisme**

### Rekomendasi KRITIS:
**QR receipt bukan flow utama untuk MVP.** Buat SMS receipt sebagai default, QR sebagai optional. Lebih baik lagi: operator yang pegang semua data, nasabah yang tidak punya app cukup simpan nomor transaksi untuk klaim poin nanti.

---

## 5. Tantangan UX Terbesar — The Hardest UX Challenge

### Jawaban: **Transisi dari Buku Catatan ke Aplikasi**

Proposal menyebut menggantikan "buku catatan 200 halaman" — tapi ini bukan sekadar ganti tools. Ini mengubah **seluruh mental model kerja** seorang operator bank sampah.

**Mengapa ini paling sulit:**

1. **Habit yang sudah 10-20 tahun**
   - Operator sudah punya sistem yang "bekerja" — buku tulis, pulpen, calculator
   - Mereka sudah hafal halaman, format, cara mencari data
   - Peralihan ke HP = membuang semua keahlian yang sudah ada
   - **Analogi:** Bayangkan suruh chef yang sudah 20 tahun masak pakai kompor gas untuk pindah ke induction cooker — teknis lebih baik, tapi mereka akan menolak.

2. **Fear of losing data**
   - Buku catatan TIDAK PERNAH crash
   - Buku catatan TIDAK PERNAH kehabisan baterai
   - Buku catatan bisa dibuka dalam 0 detik
   - **HP = semua hal di atas bisa terjadi.** Bagaimana operator percaya HP lebih baik dari buku?

3. **Social pressure**
   - Operator mungkin merasa malu jika tidak bisa pakai HP
   - Anak-anak atau tetangga mungkin "mengambil alih" operasi karena lebih paham teknologi — ini menghilangkan otonomi operator
   - Jika operator merasa tidak kompeten, mereka akan kembali ke buku

4. **Dual system overhead**
   - Selama transisi, operator mungkin harus menjalankan DUA sistem (buku + app)
   - Ini MENAMBAH beban kerja, bukan mengurangi
   - Tidak ada jalan keluar yang baik — transisi harus tegas tapi didukung

### Solusi UX untuk Tantangan Terbesar:

**A. "Buku Digital" — Mirip Buku, Beda Media**
- Layout aplikasi meniru format buku catatan yang sudah dikenal:
  - Kolom tanggal | nama | jenis | berat | harga | total
  - Scrollable table, bukan form modern
  - Tombol "Tambah Baris" di bawah, seperti menulis baris baru
- **Operator merasa seperti sedang menulis di buku, bukan menggunakan "aplikasi"**

**B. Companion Physical — Buku + Stiker**
- Berikan buku catatan fisik yang SAMA formatnya dengan layar app
- Setiap halaman buku punya stiker QR untuk sync manual
- Operator bisa tetap menulis di buku → kapanpun ada internet → foto halaman → OCR sync
- **Bridge antara dunia fisik dan digital**

**C. Buddy System**
- Pairing operator baru dengan operator yang sudah lancar
- Komunitas operator saling bantu, bukan support center tech
- WhatsApp group regional untuk sharing tips

---

## 6. Aksesibilitas — Apa yang Hilang dari Proposal

Proposal TIDAK membahas aksesibilitas sama sekali. Untuk target pengguna ini, ini bukan luxury — ini kebutuhan dasar.

### Yang HARUS Ada:

**Visual:**
- **Minimum contrast ratio 4.5:1** untuk semua teks (WCAG AA)
- **Font size minimum 18sp** untuk body text, 24sp untuk judul
- **High contrast mode** sebagai default — bukan opsi tersembunyi
- **Tidak ada informasi hanya dengan warna** — selalu ada ikon/teks sebagai redundant (operator yang buta warna, meski tidak disadari)
- **Dark mode** — banyak operator bekerja di luar ruangan, layar terang sulit terbaca

**Motorik:**
- **Touch target minimum 48x48dp** (lebih baik 56x56dp untuk pengguna ini)
- **Tidak ada swipe gesture kompleks** — cukup tap dan scroll
- **Konfirmasi untuk destructive action** — hapus data harus konfirmasi dengan teks jelas, bukan sekadar undo toast

**Kognitif:**
- **Maximum 3 langkah untuk setiap aksi utama** — timbang, kategori, simpan. Itu saja.
- **Tidak ada pop-up, modal, atau overlay** — semua informasi inline
- **Progress indicator** untuk setiap transaksi — "1/3: Timbang, 2/3: Pilih kategori, 3/3: Selesai ✅"
- **Language: Bahasa Indonesia sederhana** — tidak ada istilah teknis

**Offline accessibility:**
- **Semua fitur inti HARUS berfungsi 100% offline** — termasuk katalog harga (cache terakhir kali sync)
- **Tidak ada fitur yang "error" atau "blank" ketika offline** — selalu ada state yang fungsional

### Rekomendasi:
Buat **"Accessibility Checklist Khusus TAUT"** yang berbeda dari WCAG standar. WCAG dirancang untuk pengguna web umum — TAUT punya profil pengguna yang sangat spesifik dan membutuhkan pendekatan yang lebih ketat di area visual dan motorik, tapi lebih fleksibel di area teknologi.

---

## 7. Yang HARUS Berubah — Must-Change Items

### 🔴 BLOKIR (Harus diubah sebelum lanjut ke fase berikutnya):

**1. QR Receipt bukan flow utama → Ganti ke SMS/fisik**
- Seperti dijelaskan di atas, QR receipt tidak bisa jadi mekanisme utama untuk pengguna yang mungkin tidak punya app, tidak bisa scan, atau tidak punya printer.
- **Action:** Ubah QR dari "default" menjadi "opsi" — SMS jadi default.

**2. Tambahkan "Offline UX Contract"**
- Proposal tidak mendefinisikan secara detail apa yang terjadi dalam setiap skenario offline.
- **Action:** Buat dokumen yang mendefinisikan semua edge case offline: crash recovery, low battery, storage penuh, conflict resolution.

**3. Tambahkan "User Journey Map" untuk Operator**
- Proposal langsung ke solusi tanpa memetakan pengalaman pengguna saat ini.
- **Action:** Sebelum mendesain screens, buat journey map: "Seorang ibu di Pasar Minggu dari bangun tidur hingga tutup bank sampah — kapan dia pakai TAUT, kapan dia tidak?"

**4. Tambahkan mekanisme onboarding yang konkret**
- "Video tutorial" dan "onboarding via kader" tidak cukup.
- **Action:** Rancang "First Run Experience" (FRE) yang bisa diselesaikan dalam 2 menit, tanpa membaca, dengan ilustrasi step-by-step.

**5. Revisi asumsi "HP Rp500 ribu" dengan benchmarking nyata**
- Apakah <15MB realistis untuk fitur yang dijanjikan?
- **Action:** Buat prototype kasar (even paper prototype) dan uji di HP target. Jangan asumsikan.

### 🟡 PENTING (Sangat dianjurkan untuk diubah):

**6. Tambahkan "Undo" mechanism yang robust**
- Operator yang salah input harus bisa memperbaiki dengan mudah.
- **Action:** Setiap transaksi punya tombol "Batal/Ubah" yang jelas.

**7. Tambahkan mode "Cepat" untuk operator yang sudah lancar**
- Setelah beberapa minggu, operator tidak mau step-by-step lagi.
- **Action:** Progressive disclosure — mode pemula (guided) dan mode cepat (shortcuts).

**8. Tambahkan "Tampilan Malam/Gelap"**
- Banyak bank sampah beroperasi di area semi-outdoor dengan pencahayaan buruk.
- **Action:** Dark mode high-contrast sebagai default untuk penggunaan outdoor.

---

## 8. Sketsa User Journey — Operator Bank Sampah

```
06:00 — Bangun, nyalakan HP
         → TAUT otomatis sync data dari semalam (jika ada internet)
         → Notifikasi: "3 transaksi menunggu sync" (atau "Semua data sudah tersimpan ✅")

07:00 — Buka bank sampah
         → Buka TAUT → Layar utama: TIGA tombol besar
           [➕ Timbang & Catat]  [📊 Lihat Harga]  [📋 Riwayat]

07:05 — Nasabah pertama datang: Pak Budi bawa 2 karung kardus
         → Tekan [➕ Timbang & Catat]
         → Layar: Pilih kategori → [📦 Kardus] [🍼 Plastik] [🥫 Kaleng] [🔧 Logam] [玻璃 Kaca] [📦 Lainnya]
         → Tekan [📦 Kardus]
         → Layar: Input berat → tombol angka besar (calculator-style)
         → Ketik: 5, kg
         → Layar konfirmasi: "Kardus 5 kg × Rp 1.500 = Rp 7.500"
         → Tekan [✅ Simpan]
         → Layar sukses: "Tersimpan! No: T-2306-001" + ikon 📱 "Kirim SMS ke Pak Budi?"
         → Tekan ya → SMS terkirim otomatis (jika online) atau antrian (jika offline)
         → Kembali ke layar utama

07:10 — Nasabah kedua, ketiga, dst... loop yang sama

12:00 — Waktu rekap
         → Tekan [📋 Riwayat]
         → Lihat: Hari ini — 12 transaksi, total 85 kg, total Rp 127.500
         → Bisa filter per kategori, per nasabah

17:00 — Tutup bank sampah
         → Lihat ringkasan harian
         → Tekan [🔄 Sync] (jika belum sync otomatis)
         → "12 transaksi sudah tersimpan di cloud ✅"

Bulanan:
         → Dashboard web (bisa diakses dari HP atau warung)
         → Rekap otomatis untuk laporan RT/kecamatan
```

---

## 9. Pertanyaan untuk CIO dan Tim

Sebelum lanjut, saya butuh klarifikasi:

1. **Untuk flow QR:** Apakah nasabah diharapkan install app juga? Jika tidak, bagaimana mereka menerima/membaca QR?
2. **Untuk kategori sampah:** Siapa yang menentukan daftar kategori awal? Harus divalidasi dengan operator lapangan.
3. **Untuk onboarding:** Apakah ada budget untuk training langsung di lokasi, atau hanya digital?
4. **Untuk HP target:** Apakah sudah ada spesifikasi HP spesifik yang dijadikan benchmark?
5. **Untuk bahasa:** Apakah ada dialek/bahasa daerah yang perlu didukung? Operator di Papua mungkin tidak nyaman dengan UI full Bahasa Indonesia formal.
6. **Untuk timbangan:** Apakah harus input manual dari timbangan, atau terintegrasi? Ini mengubah UX secara fundamental.

---

## 10. Kesimpulan

TAUT memiliki visi yang kuat dan timing yang tepat. Namun dari perspektif UX, proposal ini **terlalu berorientasi fitur dan belum cukup berorientasi pengguna**. Perubahan yang paling kritis:

1. **QR receipt bukan solusi utama** — SMS/fisik receipt lebih inklusif
2. **Offline UX harus didefinisikan secara eksplisit** — bukan hanya "anti-ghost data"
3. **Desain harus meniru mental model yang sudah ada** (buku catatan), bukan mengubah kebiasaan
4. **Aksesibilitas harus built-in sejak awal** — bukan retrofitted nanti
5. **Pengguna harus hadir dalam proses desain** — card sorting, usability testing, beta testing dengan 5-10 operator asli

> *Proposal berkata: "Fokus kita bukan bikin UI yang cantik — tapi sistem yang bisa dipakai oleh seorang ibu di Pasar Minggu."*
>
> **Saya setuju. Tapi untuk itu, kita harus PERTAMA mendesain untuk ibu di Pasar Minggu — bukan untuk tim tech.** Jika UX tidak berubah dari proposal ini, kita akan membangun sistem yang bagus di atas asumsi yang salah.

---

*Review disusun oleh UX Designer*
*23 Juni 2026*
