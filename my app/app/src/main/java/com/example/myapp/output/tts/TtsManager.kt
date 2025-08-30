package com.example.myapp.output.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
	private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
	private val _ready = MutableStateFlow(false)
	val ready: StateFlow<Boolean> = _ready

	private var speechRate: Float = 1.0f
	private var pitch: Float = 1.0f

	override fun onInit(status: Int) {
		_ready.value = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.getDefault()) >= 0
		if (_ready.value) {
			tts.setSpeechRate(speechRate)
			tts.setPitch(pitch)
		}
	}

	fun configure(rate: Float = 1.0f, pitch: Float = 1.0f, locale: Locale = Locale.getDefault()) {
		speechRate = rate
		this.pitch = pitch
		if (_ready.value) {
			tts.language = locale
			tts.setSpeechRate(rate)
			tts.setPitch(pitch)
		}
	}

	fun speak(text: String) {
		if (_ready.value) {
			tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utter_${System.currentTimeMillis()}")
		}
	}

	fun stop() {
		if (_ready.value) {
			tts.stop()
		}
	}

	fun shutdown() {
		try { tts.stop() } finally { tts.shutdown() }
	}
}

