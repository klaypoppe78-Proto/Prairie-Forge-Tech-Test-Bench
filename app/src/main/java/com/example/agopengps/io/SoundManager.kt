package com.example.agopengps.io

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Audio feedback sound generator mimicking authentic modern in-cab guidance alerts
 * with rich, pleasant musical tones (warm sine-wave arpeggio) instead of harsh piercing beeps.
 */
class SoundManager {
    private var toneGen: ToneGenerator? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
        } catch (e: Exception) {
            // Audio service may not be available in headless test environment
        }
    }

    /**
     * Synthesizes and plays a smooth musical sine-wave tone with natural envelope decay.
     * Prevents ear-piercing square waves and click artifacts.
     */
    private fun playSynthesizedTone(freqHz: Double, durationMs: Int, volume: Float = 0.8f) {
        try {
            val sampleRate = 44100
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            if (numSamples <= 0) return

            val pcmBuffer = ShortArray(numSamples)
            val decayTime = numSamples * 0.75

            for (i in 0 until numSamples) {
                val time = i.toDouble() / sampleRate
                val rawSine = sin(2.0 * PI * freqHz * time)

                // Smooth attack (first 4ms) to eliminate audio click
                val attackSamples = (sampleRate * 0.004).toInt()
                val attackMultiplier = if (i < attackSamples) i.toDouble() / attackSamples else 1.0

                // Smooth exponential decay
                val decayMultiplier = exp(-3.0 * (i.toDouble() / decayTime).coerceAtMost(2.0))
                val envelope = attackMultiplier * decayMultiplier * volume

                val sampleVal = (rawSine * envelope * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcmBuffer[i] = sampleVal.toShort()
            }

            val bufferSize = numSamples * 2 // 2 bytes per 16-bit PCM sample
            val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC
                )
            }

            audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
            audioTrack.play()

            // Release AudioTrack after playback
            CoroutineScope(Dispatchers.Default).launch {
                delay(durationMs.toLong() + 50L)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            // Fallback to ToneGenerator if AudioTrack synthesis fails
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs)
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Melodic auto-steer engage chime (C5 523Hz -> G5 784Hz).
     */
    fun playAutoSteerEngage(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playSynthesizedTone(523.25, 90, 0.75f) // C5
            delay(95)
            playSynthesizedTone(783.99, 130, 0.85f) // G5
        }
    }

    /**
     * Soft auto-steer disengage descending note (G5 784Hz -> C5 523Hz).
     */
    fun playAutoSteerDisengage(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playSynthesizedTone(783.99, 80, 0.75f) // G5
            delay(90)
            playSynthesizedTone(523.25, 140, 0.70f) // C5
        }
    }

    /**
     * Boundary approach reminder note (E5 659Hz).
     */
    fun playBoundaryAlert(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playSynthesizedTone(659.25, 160, 0.80f) // E5
        }
    }

    /**
     * Pleasant 3-note harmonic arpeggio chime when approaching the headland turn.
     * Musical sequence: C5 (523.25 Hz) -> G5 (783.99 Hz) -> C6 (1046.50 Hz).
     * Smooth, clear, and harmonic without being shrill or ear-piercing.
     */
    fun playEndOfPassChime(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playSynthesizedTone(523.25, 85, 0.75f)  // C5 (523 Hz)
            delay(105)
            playSynthesizedTone(783.99, 85, 0.80f)  // G5 (784 Hz)
            delay(105)
            playSynthesizedTone(1046.50, 160, 0.85f) // C6 (1046 Hz)
        }
    }

    fun release() {
        toneGen?.release()
        toneGen = null
    }
}
