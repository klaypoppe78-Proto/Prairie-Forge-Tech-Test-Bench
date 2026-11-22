package com.example.agopengps.io

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class SoundManager {

    private fun playTone(freqHz: Double, durationMs: Int, volume: Double = 0.8) {
        val sampleRate = 44100
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i / (sampleRate / freqHz)
            val sample = (sin(angle) * Short.MAX_VALUE * volume).toInt().toShort()
            buffer[i] = sample
        }

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
        } catch (e: Exception) {
            // Ignore if audio is unavailable
        }
    }

    fun playEngageChime(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playTone(880.0, 100, 0.8) // A5
            playTone(1320.0, 150, 0.9) // E6
        }
    }

    fun playAutoSteerEngage(scope: CoroutineScope) = playEngageChime(scope)

    fun playDisengageChime(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playTone(1100.0, 100, 0.8)
            playTone(550.0, 200, 0.8)
        }
    }

    fun playAutoSteerDisengage(scope: CoroutineScope) = playDisengageChime(scope)

    fun playOperatorOverrideAlert(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            // Rapid double-beep warning alarm for emergency operator steering wheel override
            playTone(1600.0, 70, 1.0)
            playTone(400.0, 30, 0.0)
            playTone(1600.0, 70, 1.0)
            playTone(400.0, 30, 0.0)
            playTone(1200.0, 120, 0.9)
        }
    }

    fun playSnapChime(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playTone(1760.0, 80, 0.6)
        }
    }

    fun playClickTone(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playTone(1200.0, 40, 0.5)
        }
    }

    fun playEndOfPassChime(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playTone(600.0, 120, 0.8)
            playTone(600.0, 120, 0.8)
            playTone(900.0, 250, 0.9)
        }
    }

    fun release() {
        // Cleanup if needed
    }
}
