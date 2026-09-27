package com.dev.diksha.civic

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechRecognitionManager(context: Context) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    var onStateChanged: (CivicUiStage) -> Unit = {}
    var onTranscript: (String) -> Unit = {}
    var onError: (String) -> Unit = {}

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { onStateChanged(CivicUiStage.Listening) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { onStateChanged(CivicUiStage.Transcribing) }
            override fun onError(error: Int) { onError("Speech recognition could not complete. You can try again or type your question.") }
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onError("No English speech was detected.") else onTranscript(text)
            }
            override fun onPartialResults(results: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
    }

    fun start() {
        onStateChanged(CivicUiStage.Listening)
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        })
    }

    fun stop() = recognizer.stopListening()
    fun release() = recognizer.destroy()
}
