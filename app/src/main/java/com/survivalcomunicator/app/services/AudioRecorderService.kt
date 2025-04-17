package com.survivalcomunicator.app.services

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.IOException

class AudioRecorderService(private val context: Context) {
    
    private var recorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    
    @Suppress("DEPRECATION")
    fun startRecording(filePath: String) {
        try {
            currentFilePath = filePath
            
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(filePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                
                try {
                    prepare()
                } catch (e: IOException) {
                    throw IOException("Error preparando el grabador: ${e.message}")
                }
                
                start()
            }
        } catch (e: Exception) {
            releaseRecorder()
            throw Exception("Error iniciando la grabación: ${e.message}")
        }
    }
    
    fun stopRecording(): String? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val path = currentFilePath
            currentFilePath = null
            path
        } catch (e: Exception) {
            releaseRecorder()
            null
        }
    }
    
    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            // Ignorar error
        } finally {
            recorder = null
        }
    }
}