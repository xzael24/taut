package com.taut.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS wrapper for audio feedback.
 *
 * Per §7.1 TTS Engine:
 * - Engine: Android built-in TTS (offline, Indonesian voice)
 * - Speed: 0.85x for clarity
 * - Volume: 80% of system media volume
 * - Events: transaction saved, SMS sent, PIN incorrect, etc.
 *
 * All audio is paired with visual — never audio-only.
 */
class TtsManager(
    private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    /**
     * Initialize TTS engine with Indonesian locale.
     * Should be called on app startup.
     */
    fun initialize(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("id", "ID"))
                tts?.setSpeechRate(0.85f)
                isReady = true
                onReady()
            }
        }
    }

    /**
     * Speak text. Designed for transaction confirmations,
     * error announcements, and step completions.
     */
    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "taut_tts")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

/**
 * Pre-defined TTS messages for common events (§7.2 TTS Events).
 * All messages in Bahasa Indonesia sederhana.
 */
object TtsMessages {
    fun transactionSaved(weight: String, category: String, price: String): String {
        return "Transaksi tersimpan. $weight $category. $price."
    }

    fun smsSent() = "SMS terkirim ke nomor nasabah."
    fun smsQueued() = "SMS akan dikirim saat online."
    fun pinIncorrect() = "PIN salah."
    fun step1Complete() = "Berat tercatat."
    fun step2Complete() = "Kategori terpilih."
    fun transactionVoided() = "Transaksi dibatalkan."
    fun saveFailed() = "Gagal menyimpan. Coba lagi."
}
