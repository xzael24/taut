# Round 1: Proposal — Chief Innovation Officer

---

## Project: **TAUT**

**Tagline:** *Menghubungkan sampah dari sumber ke solusi — transparan, adil, dan digital.*

---

## 1. Problem Statement

Indonesia menghadapi **krisis sampah yang semakin parah**, diperburuk oleh fragmentasi ekosistem daur ulang informal yang menjadi tulang punggung pengelolaan sampah nasional.

### Fakta Lapangan (2026):

- Indonesia menghasilkan **±70 juta ton sampah per tahun**; ~40% di antaranya tidak terkelola dengan baik — dibakar, dibuang ke sungai, atau berakhir di laut.
- **Bank Sampah** (waste banks) tersebar di **11.000+ lokasi** nasional, namun >80% masih menggunakan **buku catatan manual, kertas timbangan, dan amplop cash** — data tidak pernah terpusat, tidak bisa diaudit, dan tidak bisa diverifikasi.
- **Pemulung** — sekitar **3–4 juta orang** — adalah rantai pertama daur ulang, tetapi bekerja tanpa transparansi harga, tanpa akses ke sistem keuangan formal, dan dengan margin yang ditentukan sepihak oleh pengepul.
- **Harga sampah anorganik** (plastik, kertas, logam, kaca) sangat **fluktuatif dan tidak transparan** — pemulung dan bank sampah tidak punya informasi untuk menawar harga wajar.
- **Pemerintah daerah** tidak memiliki data real-time tentang volume sampah yang didaur ulang vs. dibuang ke TPA, sehingga kebijakan berbasis data nyaris mustahil.
- **Tekanan 2026**: Larangan impor sampah plastik mulai diberlakukan ketat di Asia Tenggara, memaksa Indonesia meningkatkan kapasitas daur ulang domestik. Regulasi **UU Pengelolaan Sampah** dan **Perpres Kebijakan Kelautan** mewajibkan pelaporan data timbulan sampah yang akurat. **Tekanan ESG** dari investor dan eksportir menuntut bukti rantai pasok berkelanjutan.

### Akar Masalah

Bukan kurangnya kesadaran — tetapi **tidak adanya infrastruktur digital yang menghubungkan seluruh rantai daur ulang** dengan cara yang sederhana, offline-friendly, dan menguntungkan semua pihak.

---

## 2. Target Users (Primary → Secondary)

| Tier | User | Jumlah (Estimasi) | Pain Point Utama |
|------|------|-------------------|------------------|
| **P1** | **Operator Bank Sampah** (RT/RW, kelurahan) | 50.000+ unit aktif nasional | Catatan manual, rekap bulanan memakan waktu, laporan ke dinas tidak pernah tepat waktu |
| **P2** | **Pemulung / Pengepul Kecil** | 3–4 juta orang | Tidak tahu harga pasar, dibayar cash tanpa bukti, tidak punya akses ke pembeli yang lebih baik |
| **P3** | **Kepala Keluarga / Individu** (penyetor sampah) | 5–10 juta rumah tangga potensial | Ingin memilah dan menyetor sampah tapi repot, tidak ada insentif yang jelas |
| **S1** | **Pengepul Besar / Aggregator / Pabrik Daur Ulang** | Ratusan per kota | Supply tidak konsisten, kualitas tidak terjamin, butuh data volume untuk kapasitas produksi |
| **S2** | **Dinas Lingkungan Hidup (DLH) / Pemerintah Daerah** | 514 kabupaten/kota | Tidak punya data terpercaya untuk perencanaan, pelaporan, dan evaluasi kebijakan |

---

## 3. Proposed Solution

**TAUT** adalah **platform mobile-first dan offline-capable** yang mendigitalkan seluruh rantai daur ulang sampah anorganik — dari rumah tangga, ke bank sampah, ke pengepul, hingga ke pabrik daur ulang — dengan transparansi harga, pencatatan otomatis, dan insentif digital.

### Tiga Lapisan Inti:

#### Lapisan 1: **Catat** (Digital Ledger untuk Bank Sampah)
- Aplikasi Android ringan (< 15MB) yang bekerja **offline-first** — cukup HP Rp500 ribuan, tanpa paket data untuk operasi harian.
- Menggantikan buku catatan kertas: timbang sampah → pilih kategori (botol plastik, kertas karton, besi, dll) → input berat → otomatis tercatat.
- Setiap transaksi menghasilkan **QR code digital** sebagai bukti setor untuk nasabah.
- Sinkronisasi data saat terhubung internet.

#### Lapisan 2: **Harga** (Market Reference Pricing)
- Menampilkan **harga acuan harian** untuk 50+ kategori sampah anorganik, diambil dari data pengepul dan pabrik.
- Bank sampah dan pemulung bisa melihat harga wajar sebelum menjual — mengurangi praktik monopoli harga.
- Pengepul bisa memasang harga beli mereka sendiri untuk menarik pasokan.

#### Lapisan 3: **Tumbuh** (Insentif & Data)
- Nasabah (rumah tangga) mendapat **poin digital** setiap setor sampah. Poin bisa ditukar ke **e-money / QRIS** (via ShopeePay, GoPay, LinkAja, atau transfer bank) atau **sembako** bekerja sama dengan mitra lokal.
- Operator bank sampah mendapat **dashboard sederhana** berisi volume bulanan, tren, dan laporan otomatis untuk DLH.
- Pemerintah daerah mendapat **data agregat real-time** tentang tingkat daur ulang per kecamatan — tanpa perlu survei manual.

### Bagaimana Cara Kerjanya (End-to-End Flow):

```
Rumah Tangga       Bank Sampah           Pengepul          Pabrik
    │                   │                    │                │
    │  setor sampah ──► │                    │                │
    │                   │  timbang + catat   │                │
    │  terima QR ──────►│  di app TAUT       │                │
    │                   │                    │                │
    │                   │  harga acuan ◄─────┤                │
    │                   │                    │                │
    │                   │  jual ke pengepul ──►               │
    │                   │  (tercatat sistem)  │  kirim ke ───►│
    │                   │                    │  pabrik        │
    │                   │                    │                │
    │ ◄── poin/uang ────┤                    │                │
    │ (via QRIS)        │  laporan DLH ◄─────┤                │
```

---

## 4. Why This Matters NOW (2026)

**Tepat waktu karena konvergensi tekanan:**

1. **Regulasi:** UU Nomor 18 Tahun 2008 jo. PP 81/2012 tentang Pengelolaan Sampah kini diperkuat dengan Perpres baru di 2025–2026 yang mewajibkan pemda memiliki data timbulan sampah digital. Tanpa alat seperti TAUT, pemda tidak bisa mematuhi regulasi ini secara realistis.

2. **Ekonomi:** Inflasi pangan dan kenaikan harga BBM di 2025–2026 memukul daya beli kelas menengah ke bawah. Sampah anorganik (botol plastik, kardus, logam) menjadi **sumber pendapatan tambahan**. Keluarga bisa mendapat Rp50.000–200.000/bulan dari sampah rumah tangga — signifikan bagi 40% terbawah populasi.

3. **Lingkungan:** Target Indonesia mengurangi sampah plastik laut 70% pada 2025 tidak tercapai. Revisi target 2030 membutuhkan **data aktual** dan **insentif berbasis bukti** — bukan sekadar kampanye.

4. **Teknologi:** QRIS sudah mencapai **>50 juta pengguna** (2026). Ponsel sudah dimiliki 95%+ rumah tangga Indonesia. Infrastruktur untuk micro-payment dan tracking digital sekarang sudah matang — tetapi belum dimanfaatkan untuk sektor sampah.

5. **ESG & Ekspor:** Perusahaan Indonesia yang mengekspor ke Eropa menghadapi tekanan **CBAM (Carbon Border Adjustment Mechanism)** dan CSRD yang mewajibkan pelaporan rantai pasok berkelanjutan. TAUT menyediakan data traceability dari sumber.

---

## 5. High-Level Scope

### ✅ IN SCOPE (MVP & Post-MVP)

**MVP (Fase 1 — 3 bulan):**
- Aplikasi Android offline-capable untuk operator Bank Sampah (pencatatan, kategori sampah, QR receipt)
- Katalog harga acuan sederhana (20 kategori utama)
- Dashboard web dasar untuk operator (rekap bulanan, tren)
- Autentikasi via nomor telepon (SMS OTP)
- Sinkronisasi data via periodic sync when online

**Fase 2 (3–6 bulan):**
- Modul penukaran poin QRIS/e-money
- Harga real-time dari pengepul mitra
- Dashboard DLH (data agregat per kecamatan)
- Notifikasi pickup untuk pengepul

**Fase 3 (6–12 bulan):**
- Fitur pemesanan pickup untuk rumah tangga (on-demand)
- Integrasi dengan TPA / tempat pembuangan akhir (data tonase)
- Onboarding pengepul besar dan pabrik daur ulang
- Basic analytics & reporting untuk pelaporan CSR/ESG

### ❌ OUT OF SCOPE

- **Tidak** memproses/mendaur ulang sampah secara fisik — TAUT adalah platform data dan transaksi, bukan pengelola sampah langsung
- **Tidak** menggunakan AI/ML computer vision untuk sorting otomatis — terlalu kompleks dan mahal untuk MVP; operator tetap input manual
- **Tidak** membuat marketplace B2B untuk jual-beli sampah skala industri — cukup koneksi bank sampah ⇄ pengepul lokal
- **Tidak** menangani sampah organik dan B3 pada fase awal — fokus pada anorganik yang mudah diverifikasi dan memiliki nilai ekonomi
- **Tidak** menjadi aplikasi super-app — fokus pada satu fungsi yang dilakukan dengan sangat baik

---

## 6. Why "TAUT"?

**Taut** berarti **terhubung, berkaitan, bersambung**. Dalam konteks proyek ini:
- Menghubungkan **rumah tangga** ke ekosistem daur ulang formal
- Menghubungkan **bank sampah** ke data dan harga yang transparan
- Menghubungkan **pemda** ke informasi yang mereka butuhkan untuk kebijakan berbasis data
- Menghubungkan **sampah** — yang selama ini dianggap tidak bernilai — ke **ekonomi sirkular yang nyata**

Dan dalam bahasa Inggris, *"taut"* berarti kencang/erat — menggambarkan rantai yang tidak putus: dari sampah rumah tangga → daur ulang → produk baru.

---

## 7. Risiko & Pertimbangan Awal

| Risiko | Mitigasi |
|--------|----------|
| Literasi digital rendah di operator bank sampah (rata-rata usia 40–55) | UI icon-based minimalis; onboarding via komunitas/kader; video tutorial singkat |
| Koneksi internet tidak stabil di daerah | Offline-first dengan periodic sync; anti-ghost data saat offline |
| Resistensi dari pengepul yang diuntungkan oleh ketidaktransparanan harga | Onboarding bertahap; pengepul mitra mendapat prioritas akses pasokan |
| Keberlanjutan setelah fase awal habis | Model komisi kecil dari volume transaksi (0.5–1%); potensi CSR/ESG funding dari korporasi |
| Regulasi data privasi (UU PDP) | Data desentralisasi; pengguna kontrol data mereka; compliance sejak awal desain |

---

## 8. Pesan untuk Tim

> *Ini bukan "aplikasi bank sampah biasa." Ini adalah infrastruktur data untuk ekonomi sirkular Indonesia. Fokus kita bukan bikin UI yang cantik — tapi sistem yang bisa dipakai oleh seorang ibu di Pasar Minggu yang mengelola bank sampah dengan buku tulis 200 halaman, dan membuat data sampah Indonesia akhirnya terlihat jelas.*

**Saya usul kita mulai dengan diskusi:**
1. Apa yang paling bermasalah dari proposal ini menurut masing-masing role?
2. Kritik paling keras — apa yang akan gagal?
3. Apa asumsi saya yang paling lemah?

Saya tunggu masukan dari Product Manager, System Architect, UX Designer, Frontend Engineer, Backend Engineer, dan QA Engineer sebelum kita lanjut ke breakdown teknis.

---

*Proposal disusun oleh Chief Innovation Officer*
*23 Juni 2026*
