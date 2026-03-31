package com.samocat.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceEngine(private val service: AssistantService) {

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isTtsReady = false
    private var isWakeWordMode = true

    fun start() {
        initTts()
        initRecognizer()
        startListening()
    }

    fun stop() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        handler.removeCallbacksAndMessages(null)
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    private fun initTts() {
        tts = TextToSpeech(service) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                tts?.setSpeechRate(1.0f)
                isTtsReady = true
            }
        }
    }

    private fun initRecognizer() {
        handler.post {
            recognizer = SpeechRecognizer.createSpeechRecognizer(service)
            recognizer?.setRecognitionListener(object : RecognitionListener {

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    ) ?: return
                    val text = matches[0].lowercase().trim()
                    handleSpeech(text)
                    handler.postDelayed({ startListening() }, 500)
                }

                override fun onError(error: Int) {
                    handler.postDelayed({ startListening() }, 1000)
                }

                override fun onEndOfSpeech() {}
                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onRmsChanged(rmsdB: Float) {}
            })
        }
    }

    private fun startListening() {
        if (isListening) return
        handler.post {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            try {
                recognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                isListening = false
                handler.postDelayed({ startListening() }, 2000)
            }
        }
    }

    private fun handleSpeech(text: String) {
        isListening = false
        val wakeWord = service.getWakeWord().lowercase().trim()

        if (isWakeWordMode) {
            if (text.contains(wakeWord)) {
                isWakeWordMode = false
                speak(service.getString(R.string.tts_listening))
                handler.postDelayed({
                    isWakeWordMode = true
                }, 8000)
            }
            return
        }

        isWakeWordMode = true
        processCommand(text)
    }

    private fun processCommand(text: String) {
        when {
            text.contains("расскажи") ||
            text.contains("информация") ||
            text.contains("заказ") && text.contains("что") -> {
                service.speakOrderInfo()
            }

            text.contains("начать доставку") ||
            text.contains("начинаю доставку") ||
            text.contains("поехал") -> {
                val clicked = service.clickButton("Начать доставку")
                if (clicked) speak(service.getString(R.string.tts_delivery_started))
                else speak("Кнопка не найдена")
            }

            text.contains("завершить доставку") ||
            text.contains("доставил") ||
            text.contains("готово") -> {
                val clicked = service.clickButton("Завершить доставку")
                if (clicked) speak(service.getString(R.string.tts_delivery_finished))
                else speak("Кнопка не найдена")
            }

            text.contains("в даркторе") ||
            text.contains("вернулся") ||
            text.contains("я на месте") -> {
                val clicked = service.clickButton("Я в даркторе")
                if (clicked) speak(service.getString(R.string.tts_darkstore_confirmed))
                else speak("Кнопка не найдена")
            }

            else -> {
                speak("Не понял команду. Попробуй ещё раз.")
            }
        }
    }
}
