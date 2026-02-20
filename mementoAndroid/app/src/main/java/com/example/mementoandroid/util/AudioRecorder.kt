package com.example.mementoandroid.util

import android.content.Context
import android.media.MediaRecorder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Records audio to a temporary file in the app cache directory.
 * File is not persisted to permanent storage; caller can move/save it later if needed.
 *
 * @param context Used to get cache dir and create the output file.
 * @param photoId Used to name the file so each photo has a distinct recording (e.g. for later persistence keying).
 * @param stopChannel Receive one element to stop recording; the coroutine will stop and return the file path.
 * @return Absolute path of the recorded file (e.g. .../cache/photo_1_voice.m4a), or null if recording failed.
 */
suspend fun recordAudioToCache(
    context: Context,
    photoId: String,
    stopChannel: Channel<Unit>
): String? = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "photo_${photoId}_voice.m4a")
    var recorder: MediaRecorder? = null
    try {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setAudioChannels(1)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        stopChannel.receive()
        recorder.stop()
        recorder.release()
        recorder = null
        file.absolutePath
    } catch (e: Exception) {
        recorder?.release()
        null
    }
}
