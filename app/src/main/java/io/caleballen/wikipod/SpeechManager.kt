package io.caleballen.wikipod

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import timber.log.Timber
import java.util.*

/**
 * Created by caleb on 10/19/2017.
 */

class SpeechManager(context: Context, val callback: (String) -> Unit) : RecognitionListener {
    var speechRecognizer : SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var isListening = false
    init {
        speechRecognizer.setRecognitionListener(this)
    }
    fun listen(){
        val speechIntent = Intent()
        speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command")
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
        speechRecognizer.startListening(speechIntent)
        isListening = true
    }

    fun isListening() : Boolean{
        return isListening
    }

    fun stop(){
        isListening = false
        speechRecognizer.stopListening()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Timber.v("onReadyForSpeach")
    }

    override fun onRmsChanged(rmsdB: Float) {

    }

    override fun onBufferReceived(buffer: ByteArray?) {

    }

    override fun onPartialResults(partialResults: Bundle?) {

    }

    override fun onEvent(eventType: Int, params: Bundle?) {

    }

    override fun onBeginningOfSpeech() {
        Timber.v("onBeginningOfSpeech")
    }

    override fun onEndOfSpeech() {
        Timber.v("onEndOfSpeech")
        isListening = false
    }

    override fun onError(error: Int) {
        Timber.e("onError: $error")
        isListening = false
    }

    override fun onResults(results: Bundle) {
        Timber.d("onResults: $results")
        isListening = false
        val guesses = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        Timber.e(guesses.size.toString())
        Timber.e(confidence.size.toString())


        for (i in guesses.indices) {
            Timber.d("${guesses[i]} - ${confidence[i]}")
        }

        callback(guesses.first())
//        guesses.forEach { Timber.d(it) }
    }

}