-- ===================================================
-- TAUT — Platform Daur Ulang Digital
-- Version: V1_0_1
-- Description: Seed reference data — provinces, cities,
--              districts, villages, waste categories,
--              ledger accounts
-- ===================================================

-- ──── Provinces ────
-- Indonesia has 38 provinces as of 2026
INSERT INTO provinsis (id, name_id, iso_code) VALUES
    (1, 'Aceh', 'ID-AC'),
    (2, 'Sumatera Utara', 'ID-SU'),
    (3, 'Sumatera Barat', 'ID-SB'),
    (4, 'Riau', 'ID-RI'),
    (5, 'Jambi', 'ID-JA'),
    (6, 'Sumatera Selatan', 'ID-SS'),
    (7, 'Bengkulu', 'ID-BE'),
    (8, 'Lampung', 'ID-LA'),
    (9, 'Kepulauan Bangka Belitung', 'ID-BB'),
    (10, 'Kepulauan Riau', 'ID-KR'),
    (11, 'DKI Jakarta', 'ID-JK'),
    (12, 'Jawa Barat', 'ID-JB'),
    (13, 'Jawa Tengah', 'ID-JT'),
    (14, 'DI Yogyakarta', 'ID-YO'),
    (15, 'Jawa Timur', 'ID-JI'),
    (16, 'Banten', 'ID-BT'),
    (17, 'Bali', 'ID-BA'),
    (18, 'Nusa Tenggara Barat', 'ID-NB'),
    (19, 'Nusa Tenggara Timur', 'ID-NT'),
    (20, 'Kalimantan Barat', 'ID-KB'),
    (21, 'Kalimantan Tengah', 'ID-KT'),
    (22, 'Kalimantan Selatan', 'ID-KS'),
    (23, 'Kalimantan Timur', 'ID-KI'),
    (24, 'Kalimantan Utara', 'ID-KU'),
    (25, 'Sulawesi Utara', 'ID-SA'),
    (26, 'Sulawesi Tengah', 'ID-ST'),
    (27, 'Sulawesi Selatan', 'ID-SN'),
    (28, 'Sulawesi Tenggara', 'ID-SG'),
    (29, 'Gorontalo', 'ID-GO'),
    (30, 'Sulawesi Barat', 'ID-SR'),
    (31, 'Maluku', 'ID-MA'),
    (32, 'Maluku Utara', 'ID-MU'),
    (33, 'Papua', 'ID-PA'),
    (34, 'Papua Barat', 'ID-PB'),
    (35, 'Papua Selatan', 'ID-PS'),
    (36, 'Papua Tengah', 'ID-PT'),
    (37, 'Papua Pegunungan', 'ID-PE'),
    (38, 'Papua Barat Daya', 'ID-PD');

-- ──── Waste Categories ────
-- 20 core categories with default prices (in satuan rupiah per kg)
INSERT INTO waste_categories (id, code, name_id, name_en, category_group, unit_type, unit_price, sort_order) VALUES
    (gen_random_uuid(), 'KRD-01', 'Kardus/Duplex', 'Cardboard/Duplex', 'kardus', 'kg', 2500, 1),
    (gen_random_uuid(), 'KRD-02', 'Kardus Campur', 'Mixed Cardboard', 'kardus', 'kg', 1500, 2),
    (gen_random_uuid(), 'KRT-01', 'Kertas Putih', 'White Paper', 'kertas', 'kg', 2000, 3),
    (gen_random_uuid(), 'KRT-02', 'Kertas Campur', 'Mixed Paper', 'kertas', 'kg', 1000, 4),
    (gen_random_uuid(), 'KRT-03', 'Koran/Majalah', 'Newspaper/Magazine', 'kertas', 'kg', 1200, 5),
    (gen_random_uuid(), 'PLT-01', 'Botol PET', 'PET Bottles', 'plastik', 'kg', 4000, 6),
    (gen_random_uuid(), 'PLT-02', 'Plastik HDPE', 'HDPE Plastic', 'plastik', 'kg', 3500, 7),
    (gen_random_uuid(), 'PLT-03', 'Plastik Campur', 'Mixed Plastic', 'plastik', 'kg', 1500, 8),
    (gen_random_uuid(), 'PLT-04', 'Kresek/Bakul', 'Plastic Bags', 'plastik', 'kg', 500, 9),
    (gen_random_uuid(), 'GLS-01', 'Botol Kaca', 'Glass Bottles', 'kaca', 'kg', 800, 10),
    (gen_random_uuid(), 'GLS-02', 'Kaca Campur', 'Mixed Glass', 'kaca', 'kg', 400, 11),
    (gen_random_uuid(), 'LGM-01', 'Aluminium', 'Aluminum', 'logam', 'kg', 12000, 12),
    (gen_random_uuid(), 'LGM-02', 'Besi', 'Iron', 'logam', 'kg', 3000, 13),
    (gen_random_uuid(), 'LGM-03', 'Tembaga', 'Copper', 'logam', 'kg', 55000, 14),
    (gen_random_uuid(), 'LGM-04', 'Kuningan', 'Brass', 'logam', 'kg', 35000, 15),
    (gen_random_uuid(), 'LGM-05', 'Kabel', 'Cable', 'logam', 'kg', 15000, 16),
    (gen_random_uuid(), 'ELK-01', 'Elektronik Kecil', 'Small Electronics', 'elektronik', 'kg', 5000, 17),
    (gen_random_uuid(), 'ELK-02', 'Baterai', 'Batteries', 'elektronik', 'kg', 2000, 18),
    (gen_random_uuid(), 'LNY-01', 'Minyak Jelantah', 'Used Cooking Oil', 'lainnya', 'liter', 6000, 19),
    (gen_random_uuid(), 'LNY-02', 'Styrofoam', 'Styrofoam', 'lainnya', 'kg', 300, 20);

-- ──── Ledger Accounts ────

INSERT INTO ledger_accounts (id, account_code, name_id, account_type, normal_balance) VALUES
    (gen_random_uuid(), '1000', 'Kas — Rekening Operasional TAUT', 'asset', 'debit'),
    (gen_random_uuid(), '2000', 'Liabilitas — Poin Nasabah', 'liability', 'credit'),
    (gen_random_uuid(), '2100', 'Liabilitas — Payout Tertunda', 'liability', 'credit'),
    (gen_random_uuid(), '3000', 'Ekuitas — Laba Ditahan', 'equity', 'credit'),
    (gen_random_uuid(), '4000', 'Pendapatan — Komisi', 'revenue', 'credit'),
    (gen_random_uuid(), '4100', 'Pendapatan — Data-as-Service', 'revenue', 'credit'),
    (gen_random_uuid(), '5000', 'Beban — SMS', 'expense', 'debit'),
    (gen_random_uuid(), '5100', 'Beban — Redemption Poin', 'expense', 'debit'),
    (gen_random_uuid(), '5200', 'Beban — Operasional Platform', 'expense', 'debit');
