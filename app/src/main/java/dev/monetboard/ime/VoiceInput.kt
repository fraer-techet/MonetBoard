package dev.monetboard.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/** Voice dictation through the system SpeechRecognizer. Partial text is shown live, finals are committed to the field. */
class VoiceInput(private val ctx: Context, private val c: KeyboardController) {
    private var sr: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wanted = false

    fun hasPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        val st = c.state
        st.voiceError = null
        st.voiceNeedsPermission = false
        st.voiceText = ""
        if (!hasPermission()) {
            st.voiceNeedsPermission = true
            st.voiceError = "Нужен доступ к микрофону"
            c.openPermission()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            st.voiceError = "На устройстве нет сервиса распознавания речи"
            return
        }
        wanted = true
        listen()
    }

    private fun listen() {
        val st = c.state
        sr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { st.voiceListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { st.voiceLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(b: Bundle?) {
                st.voiceText = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }

            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                st.voiceText = ""
                if (t.isNotBlank()) c.commitVoice(t)
                if (wanted && c.prefs.voiceContinuous) handler.postDelayed({ if (wanted) listen() }, 120)
                else stop()
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_CLIENT) return // fired after our own cancel/destroy
                st.voiceError = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не расслышал. Нажми на микрофон ещё раз"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { st.voiceNeedsPermission = true; "Нужен доступ к микрофону" }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Нет сети для распознавания"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят, попробуй ещё раз"
                    else -> "Ошибка распознавания ($error)"
                }
                stop()
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, st.lang.tag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        r.startListening(i)
    }

    fun stop() {
        wanted = false
        handler.removeCallbacksAndMessages(null)
        sr?.cancel()
        sr?.destroy()
        sr = null
        c.state.voiceListening = false
        c.state.voiceLevel = 0f
        c.state.voiceText = ""
    }
}
