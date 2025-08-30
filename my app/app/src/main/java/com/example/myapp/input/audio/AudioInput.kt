package com.example.myapp.input.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class AudioInput(private val context: Context) {
    private val TAG = "[AUDIO_INPUT]"

    fun pcmFlow(): Flow<ByteArray> = callbackFlow {
        Log.i(TAG, "🎙️ pcmFlow start")

        ensureRecordPermission()
        Log.i(TAG, "RECORD_AUDIO permission granted")

        val tryRates = intArrayOf(16000, 44100, 48000, 11025, 8000)
        val trySources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )

        var recorder: AudioRecord? = null
        var chosenRate = 0
        var chosenSource = 0
        var bufferSize = 0

        outer@ for (src in trySources) {
            for (rate in tryRates) {
                val minBuf = AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) {
                    Log.w(TAG, "Min buffer size <= 0 for rate $rate, source $src")
                    continue
                }

                val actualBufferSize = minBuf * 2
                val r = AudioRecord(
                    src,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    actualBufferSize
                )

                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    // 检查物理麦克风（API 28+）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val mic = r.activeMicrophones.firstOrNull()
                        Log.i(TAG, "Active microphone type=${mic?.type ?: -1}")
                    }
                    recorder = r
                    chosenRate = rate
                    chosenSource = src
                    bufferSize = actualBufferSize
                    Log.i(TAG, "✅ Initialized successfully: source=$chosenSource, rate=$chosenRate, bufferSize=$bufferSize")
                    break@outer
                } else {
                    Log.w(TAG, "AudioRecord not initialized for rate $rate, source $src")
                    r.release()
                }
            }
        }

        if (recorder == null) {
            Log.e(TAG, "No available AudioRecord configuration")
            close(IllegalStateException("AudioRecord init failed"))
            return@callbackFlow
        }

        try {
            recorder.startRecording()
            Log.i(TAG, "Recording started - state: ${recorder.state}, recordingState: ${recorder.recordingState}")
            
            // 等待录音真正开始
            var waitCount = 0
            while (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING && waitCount < 50) {
                delay(10)
                waitCount++
            }
            
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "Recording failed to start")
                close(IllegalStateException("Recording failed to start"))
                return@callbackFlow
            }
            
            Log.i(TAG, "Recording confirmed started")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            close(e)
            return@callbackFlow
        }

        val buffer = ByteArray(bufferSize)
        withContext(Dispatchers.IO) {
            var totalBytes = 0L
            var consecutiveZeroReads = 0
            val maxConsecutiveZeroReads = 10
            
            while (isActive) {
                // 检查录音状态
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.w(TAG, "Recording state is not RECORDING, current state: ${recorder.recordingState}")
                    break
                }
                
                val read = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    } else {
                        recorder.read(buffer, 0, buffer.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "read failed", e)
                    -2
                }

                when {
                    read == AudioRecord.ERROR_INVALID_OPERATION -> {
                        Log.e(TAG, "ERROR_INVALID_OPERATION")
                        break
                    }
                    read == AudioRecord.ERROR_BAD_VALUE -> {
                        Log.e(TAG, "ERROR_BAD_VALUE")
                        break
                    }
                    read == AudioRecord.ERROR_DEAD_OBJECT -> {
                        Log.e(TAG, "ERROR_DEAD_OBJECT")
                        break
                    }
                    read > 0 -> {
                        consecutiveZeroReads = 0
                        totalBytes += read
                        trySend(buffer.copyOf(read))
                    }
                    read == 0 -> {
                        consecutiveZeroReads++
                        Log.w(TAG, "read=0 (consecutive: $consecutiveZeroReads)")
                        
                        if (consecutiveZeroReads >= maxConsecutiveZeroReads) {
                            Log.e(TAG, "Too many consecutive zero reads, stopping")
                            break
                        }
                        
                        // 短暂延迟后再试
                        delay(10)
                    }
                    read < 0 -> {
                        Log.e(TAG, "read error: $read")
                        break
                    }
                }
            }
            Log.i(TAG, "Recording loop ended - totalBytes: $totalBytes")
        }

        awaitClose {
            Log.i(TAG, "Closing audio recorder")
            try {
                if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder?.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recorder", e)
            }
            try {
                recorder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing recorder", e)
            }
            Log.i(TAG, "Audio recorder released")
        }
    }

    private fun ensureRecordPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.e(TAG, "❌ RECORD_AUDIO permission missing")
            throw SecurityException("Missing RECORD_AUDIO permission")
        }
    }
}