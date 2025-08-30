package com.example.myapp.model

data class AucQueryResponse(
	val result: ResultContent? = null,
	val audio_info: AudioInfo? = null
)

data class ResultContent(
	val text: String? = null,
	val utterances: List<Utterance>? = null
)

data class Utterance(
	val definite: Boolean? = null,
	val start_time: Int? = null,
	val end_time: Int? = null,
	val text: String? = null
)

data class AudioInfo(
	val duration: Int? = null
)


