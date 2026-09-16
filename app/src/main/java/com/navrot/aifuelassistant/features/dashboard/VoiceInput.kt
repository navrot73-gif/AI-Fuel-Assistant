package com.navrot.aifuelassistant.features.dashboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

fun createSpeechRecognizerIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
    }
}

fun createSpeechRecognizer(
    context: Context,
    onResult: (String) -> Unit,
    onListening: (Boolean) -> Unit,
    onError: ((String) -> Unit)? = null
): SpeechRecognizer {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onListening(true)
        }
        override fun onBeginningOfSpeech() {
            onListening(true)
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            onListening(false)
        }

        override fun onError(error: Int) {
            onListening(false)
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи аудио"
                SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента распознавания"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на запись аудио"
                SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Превышено время ожидания сети"
                SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось распознать речь"
                SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера распознавания"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не обнаружена"
                else -> "Не удалось распознать голос"
            }
            onError?.invoke(errorMsg)
        }

        override fun onResults(results: Bundle?) {
            onListening(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                onResult(text)
            } else {
                onError?.invoke("Не удалось распознать голос")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    return recognizer
}
