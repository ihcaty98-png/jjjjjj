package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceSampleRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File {
        stopRecordingSafely()
        val dir = File(context.cacheDir, "voice_samples").apply { mkdirs() }
        val file = File(dir, "sample_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(24_000)
            setAudioEncodingBitRate(96_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        return file
    }

    fun stopRecording(): File? {
        val recorded = outputFile
        stopRecordingSafely()
        return recorded?.takeIf { it.exists() && it.length() > 1024L }
    }

    fun cancelRecording() {
        val target = outputFile
        stopRecordingSafely()
        target?.delete()
    }

    private fun stopRecordingSafely() {
        val current = recorder ?: return
        runCatching { current.stop() }
        runCatching { current.reset() }
        runCatching { current.release() }
        recorder = null
    }
}

class WavPreviewPlayer {

    private var mediaPlayer: MediaPlayer? = null

    fun playFile(file: File, onFinished: () -> Unit = {}) {
        stop()
        if (!file.exists() || file.length() <= 44L) {
            onFinished()
            return
        }
        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                stop()
                onFinished()
            }
            player.setOnErrorListener { _, _, _ ->
                stop()
                onFinished()
                true
            }
            player.prepare()
            player.start()
        }.onFailure {
            stop()
            onFinished()
        }
    }

    fun stop() {
        val current = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            if (current.isPlaying) current.stop()
        }
        runCatching { current.release() }
    }
}
