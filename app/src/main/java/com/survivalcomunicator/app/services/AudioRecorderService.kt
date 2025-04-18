// Ruta: app/src/main/java/com/survivalcomunicator/app/services/AudioRecorderService.kt
package com.survivalcomunicator.app.services

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class AudioRecorderService(private val context: Context) {
    
    private var recorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    
    @Suppress("DEPRECATION")
    fun startRecording(filePath: String) {
        try {
            // Asegurarse de que cualquier grabador existente se libere primero
            releaseRecorder()
            
            currentFilePath = filePath
            
            // Verificar si el directorio existe y crearlo si no
            val file = File(filePath)
            file.parentFile?.mkdirs()
            
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
                    releaseRecorder()
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
            }
            val path = currentFilePath
            releaseRecorder()
            currentFilePath = null
            path
        } catch (e: Exception) {
            releaseRecorder()
            null
        }
    }
    
    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
            }
        } catch (e: Exception) {
            // Ignorar errores al cancelar
        } finally {
            releaseRecorder()
            
            // Eliminar el archivo si existe
            currentFilePath?.let {
                try {
                    File(it).delete()
                } catch (e: Exception) {
                    // Ignorar errores al eliminar
                }
            }
            currentFilePath = null
        }
    }
    
    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            // Ignorar error en release
        } finally {
            recorder = null
        }
    }
    
    // Asegurarse de que los recursos se liberen cuando el servicio se destruya
    fun dispose() {
        cancelRecording()
    }
}