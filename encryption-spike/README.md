# Encryption Spike — Phase 0

## Purpose

Proves that AES-256-GCM encryption/decryption and PBKDF2 key derivation work
correctly on JVM **without** any Android framework dependencies. This spike
validates the crypto primitives that will underpin SQLCipher encryption in the
Android POS application.

## What Was Tested

| Test | Description | Result |
|------|-------------|--------|
| PBKDF2 Key Derivation | 100K iterations, SHA-256, 256-bit key output | ✓ Pass |
| AES-256-GCM Encryption | Encrypt JSON transaction data | ✓ Pass |
| AES-256-GCM Decryption | Decrypt and verify plaintext matches | ✓ Pass |
| GCM Auth Tag Integrity | Tampered/wrong key causes AEADBadTagException | ✓ Pass |
| Wrong Passphrase Rejection | GCM tag mismatch prevents decryption | ✓ Pass |
| IV/Salt Uniqueness | Each encryption produces unique IV and salt | ✓ Pass |

## Crypto Parameters

| Parameter | Value |
|-----------|-------|
| Algorithm | AES-256-GCM |
| Key Derivation | PBKDF2WithHmacSHA256 |
| PBKDF2 Iterations | 100,000 |
| Key Length | 256 bits |
| GCM IV Length | 12 bytes (96 bits) |
| GCM Tag Length | 128 bits (16 bytes) |
| Salt Length | 32 bytes (256 bits) |
| AAD | `"v1"` — version tag for future algorithm upgrades |

## Why These Choices

- **AES-256-GCM**: Authenticated encryption (AEAD) — provides both confidentiality
  and integrity in a single operation. NIST-approved. Supported by SQLCipher.
- **PBKDF2 100K iterations**: Balance between security and mobile device performance.
  Apple recommends 100K for PBKDF2-SHA256. Android Keystore handles this in hardware
  on modern devices.
- **Random IV + Salt per encryption**: Ensures CPA (Chosen Plaintext Attack) security.
  Same plaintext encrypts to different ciphertext each time.

## Running the Spike

```bash
cd encryption-spike
./gradlew run
```

Or build and run the JAR:

```bash
./gradlew shadowJar
java -jar build/libs/encryption-spike-1.0.0-all.jar
```

## Sample Output

```
╔══════════════════════════════════════════════════════════╗
║  CryptoSpike — AES-256-GCM Encryption-at-Rest Proof    ║
║  Phase 0: Proving crypto primitives on JVM             ║
╚══════════════════════════════════════════════════════════╝

── Test 1: PBKDF2 Key Derivation (100K iterations) ──
  Key derived: AES / 256 bits
  Derivation time: 85.23 ms
  ✓ Key derivation successful

── Test 2: Encrypt Sample Transaction Data ──
  ...
  ✓ Encryption successful

── Test 3: Decrypt and Verify Integrity ──
  Integrity check: ✓ PASS
  Data matches: ✓ Exact match

── Test 4: Wrong Passphrase Fails ──
  ✓ PASS — GCM authentication tag mismatch (correct rejection)

── Test 5: IV/Salt Randomness (CPA Security) ──
  Same plaintext encrypted twice:
    IVs match:   ✓ PASS (unique IVs)
    Salts match: ✓ PASS (unique salts)

══════════════════════════════════════════════════════════
  VERDICT: Crypto primitives are production-ready.
══════════════════════════════════════════════════════════
```

## Next Steps

These validated primitives will be used with:
- **SQLCipher** for database-level encryption at rest
- **Android Keystore** for hardware-backed key storage
- **PBKDF2 + device PIN** for passphrase derivation
