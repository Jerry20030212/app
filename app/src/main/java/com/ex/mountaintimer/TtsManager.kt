package com.ex.mountaintimer

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS 語音播報管理器
 * - 通過起點: "紀錄開始"
 * - 通過自訂點N: "經過自訂點N"
 * - 通過終點: "已到達終點"
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 設定中文語系
                val result = tts?.setLanguage(Locale.TRADITIONAL_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 退回中文簡體或預設
                    tts?.setLanguage(Locale.CHINESE)
                }
                isReady = true
            }
        }
    }

    /** 播報文字 */
    fun speak(text: String) {
        if (!isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gate_announce")
    }

    /** 通過起點 */
    fun announceStart() {
        speak("紀錄開始")
    }

    /** 通過自訂點 */
    fun announceCheckpoint(index: Int) {
        speak("經過自訂點$index")
    }

    /** 通過終點 */
    fun announceFinish() {
        speak("已到達終點")
    }

    /** 釋放資源 */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
